package com.retail.inventory.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.inventory.config.InventoryProperties;
import com.retail.inventory.domain.InventoryAudit;
import com.retail.inventory.domain.InventoryItem;
import com.retail.inventory.domain.ReservationStatus;
import com.retail.inventory.domain.StockReservation;
import com.retail.inventory.dto.AvailabilityResponse;
import com.retail.inventory.dto.ReservationResponse;
import com.retail.inventory.dto.ReserveItem;
import com.retail.inventory.dto.ReserveRequest;
import com.retail.inventory.exception.ConflictException;
import com.retail.inventory.exception.InvalidRequestException;
import com.retail.inventory.exception.StockShortageException;
import com.retail.inventory.repository.InventoryAuditRepository;
import com.retail.inventory.repository.InventoryItemRepository;
import com.retail.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_ITEMS = 50;
    private static final int MAX_QUANTITY_PER_PRODUCT = 1000;
    private static final int MAX_AVAILABILITY_IDS = 100;

    private final InventoryItemRepository items;
    private final StockReservationRepository reservations;
    private final InventoryAuditRepository audits;
    private final LowStockNotifier lowStock;
    private final InventoryProperties props;
    private final TransactionTemplate tx;

    public ReservationService(InventoryItemRepository items, StockReservationRepository reservations,
                              InventoryAuditRepository audits, LowStockNotifier lowStock,
                              InventoryProperties props, PlatformTransactionManager txManager) {
        this.items = items;
        this.reservations = reservations;
        this.audits = audits;
        this.lowStock = lowStock;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- reserve ----------

    /** All-or-nothing. Calling it again for the same order with the same items returns the existing reservation. */
    public ReservationResponse reserve(ReserveRequest request) {
        Long orderId = request.orderId();
        SortedMap<Long, Integer> wanted = merge(request.items());
        try {
            return tx.execute(status -> doReserve(orderId, wanted));
        } catch (DataIntegrityViolationException raced) {
            // A parallel call for the same order won: repeat once, it is then answered as a retry.
            return tx.execute(status -> doReserve(orderId, wanted));
        }
    }

    private ReservationResponse doReserve(Long orderId, SortedMap<Long, Integer> wanted) {
        List<StockReservation> existing = reservations.findByOrderIdOrderByProductIdAsc(orderId);
        if (!existing.isEmpty()) {
            return answerRetry(orderId, existing, wanted);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.reservationTtl());
        List<StockReservation> rows = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            StockReservation row = new StockReservation();
            row.setOrderId(orderId);
            row.setProductId(entry.getKey());
            row.setQuantity(entry.getValue());
            row.setStatus(ReservationStatus.RESERVED);
            row.setExpiresAt(expiresAt);
            rows.add(row);
        }
        // Inserted first: the unique key (order, product) makes a parallel duplicate wait here, before it touches stock.
        List<StockReservation> saved = reservations.saveAllAndFlush(rows);

        // Products are processed in ascending id order, so two orders can never lock rows in opposite order.
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            if (items.reserve(entry.getKey(), entry.getValue(), now) == 0) {
                int available = items.findByProductId(entry.getKey()).map(InventoryItem::available).orElse(0);
                shortages.add("product " + entry.getKey() + " (requested " + entry.getValue()
                        + ", available " + available + ")");
            }
        }
        if (!shortages.isEmpty()) {
            throw new StockShortageException(shortages);          // rolls back the rows and every stock change above
        }

        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            items.findByProductId(entry.getKey())
                    .ifPresent(item -> lowStock.notifyIfCrossed(item, item.available() + entry.getValue()));
        }
        log.info("Stock reserved: orderId={}, products={}", orderId, wanted.keySet());
        return toResponse(orderId, saved);
    }

    private ReservationResponse answerRetry(Long orderId, List<StockReservation> existing,
                                            Map<Long, Integer> wanted) {
        Map<Long, Integer> have = existing.stream()
                .collect(Collectors.toMap(StockReservation::getProductId, StockReservation::getQuantity));
        if (!have.equals(wanted)) {
            throw new ConflictException("Order " + orderId + " already has a reservation with different items");
        }
        boolean usable = existing.stream().allMatch(r ->
                r.getStatus() == ReservationStatus.RESERVED || r.getStatus() == ReservationStatus.COMMITTED);
        if (!usable) {
            throw new ConflictException("The reservation for order " + orderId
                    + " was released or has expired. Start a new order.");
        }
        return toResponse(orderId, existing);
    }

    // ---------- commit ----------

    /** Payment succeeded: the reserved units are sold. Calling it twice is harmless. */
    @Transactional
    public ReservationResponse commit(Long orderId) {
        List<StockReservation> lines = reservations.findByOrderIdOrderByProductIdAsc(orderId);
        if (lines.isEmpty()) {
            throw new ResourceNotFoundException("Reservation for order", orderId);
        }
        Instant now = Instant.now();
        for (StockReservation line : lines) {
            switch (line.getStatus()) {
                case COMMITTED -> { }                                         // already done
                case RESERVED -> commitLine(orderId, line, now);
                default -> throw new ConflictException("The reservation for order " + orderId + " is "
                        + line.getStatus() + " and cannot be committed. Refund the payment or place a new order.");
            }
        }
        return toResponse(orderId, reservations.findByOrderIdOrderByProductIdAsc(orderId));
    }

    private void commitLine(Long orderId, StockReservation line, Instant now) {
        if (reservations.transition(line.getId(), ReservationStatus.RESERVED, ReservationStatus.COMMITTED, now) == 0) {
            ReservationStatus current = reservations.findById(line.getId())
                    .map(StockReservation::getStatus).orElse(null);
            if (current == ReservationStatus.COMMITTED) {
                return;                                                       // a parallel call did it
            }
            throw new ConflictException("The reservation for order " + orderId + " changed while committing ("
                    + current + "). Refund the payment or place a new order.");
        }
        if (items.commitSale(line.getProductId(), line.getQuantity(), now) != 1) {
            throw new IllegalStateException("Inventory out of sync for product " + line.getProductId());
        }
        int onHand = items.findByProductId(line.getProductId()).map(InventoryItem::getQuantityOnHand).orElseThrow();
        audits.save(new InventoryAudit(line.getProductId(), onHand + line.getQuantity(), onHand,
                "ORDER_COMMIT order=" + orderId, "system"));
    }

    // ---------- release ----------

    /**
     * Payment failed or the order was cancelled. A RESERVED line gives its hold back; a COMMITTED line
     * (cancelled after payment) puts the units back on the shelf. An unknown order is not an error:
     * compensation calls must be safe to repeat.
     */
    @Transactional
    public ReservationResponse release(Long orderId) {
        List<StockReservation> lines = reservations.findByOrderIdOrderByProductIdAsc(orderId);
        if (lines.isEmpty()) {
            return new ReservationResponse(orderId, List.of());
        }
        Instant now = Instant.now();
        for (StockReservation line : lines) {
            if (line.getStatus() == ReservationStatus.RESERVED) {
                if (reservations.transition(line.getId(), ReservationStatus.RESERVED, ReservationStatus.RELEASED, now) == 1
                        && items.unreserve(line.getProductId(), line.getQuantity(), now) != 1) {
                    throw new IllegalStateException("Inventory out of sync for product " + line.getProductId());
                }
            } else if (line.getStatus() == ReservationStatus.COMMITTED) {
                if (reservations.transition(line.getId(), ReservationStatus.COMMITTED, ReservationStatus.RELEASED, now) == 1) {
                    if (items.restock(line.getProductId(), line.getQuantity(), now) != 1) {
                        throw new IllegalStateException("Inventory out of sync for product " + line.getProductId());
                    }
                    int onHand = items.findByProductId(line.getProductId())
                            .map(InventoryItem::getQuantityOnHand).orElseThrow();
                    audits.save(new InventoryAudit(line.getProductId(), onHand - line.getQuantity(), onHand,
                            "ORDER_CANCEL_RESTOCK order=" + orderId, "system"));
                }
            }
            // RELEASED and EXPIRED lines: nothing to do
        }
        log.info("Stock released: orderId={}", orderId);
        return toResponse(orderId, reservations.findByOrderIdOrderByProductIdAsc(orderId));
    }

    // ---------- expiry ----------

    /** Called by the expiry job. Safe on several instances: only the instance whose update changes the row continues. */
    @Transactional
    public boolean expire(Long reservationId) {
        StockReservation reservation = reservations.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED) {
            return false;
        }
        Instant now = Instant.now();
        if (reservations.transitionIfExpired(reservationId, ReservationStatus.RESERVED,
                ReservationStatus.EXPIRED, now) == 0) {
            return false;
        }
        if (items.unreserve(reservation.getProductId(), reservation.getQuantity(), now) != 1) {
            throw new IllegalStateException("Inventory out of sync for product " + reservation.getProductId());
        }
        log.info("Reservation expired: orderId={}, productId={}", reservation.getOrderId(), reservation.getProductId());
        return true;
    }

    // ---------- availability ----------

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> availability(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new InvalidRequestException("productIds is required");
        }
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(productIds));
        if (distinct.size() > MAX_AVAILABILITY_IDS) {
            throw new InvalidRequestException("At most " + MAX_AVAILABILITY_IDS + " products per request");
        }
        Map<Long, InventoryItem> byProduct = items.findByProductIdIn(distinct).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        return distinct.stream().map(id -> {
            int available = byProduct.containsKey(id) ? Math.max(byProduct.get(id).available(), 0) : 0;
            return new AvailabilityResponse(id, available, available > 0);
        }).toList();
    }

    // ---------- helpers ----------

    /** Same product twice means "add up"; the result is sorted by product id (lock order). */
    private static SortedMap<Long, Integer> merge(List<ReserveItem> list) {
        if (list == null || list.isEmpty()) {
            throw new InvalidRequestException("At least one item is required");
        }
        TreeMap<Long, Integer> map = new TreeMap<>();
        for (ReserveItem item : list) {
            map.merge(item.productId(), item.quantity(), Integer::sum);
        }
        if (map.size() > MAX_ITEMS) {
            throw new InvalidRequestException("At most " + MAX_ITEMS + " different products per order");
        }
        map.forEach((productId, quantity) -> {
            if (quantity > MAX_QUANTITY_PER_PRODUCT) {
                throw new InvalidRequestException("At most " + MAX_QUANTITY_PER_PRODUCT
                        + " units of product " + productId + " per order");
            }
        });
        return map;
    }

    private static ReservationResponse toResponse(Long orderId, List<StockReservation> lines) {
        return new ReservationResponse(orderId, lines.stream()
                .map(l -> new ReservationResponse.Line(l.getProductId(), l.getQuantity(), l.getStatus(), l.getExpiresAt()))
                .toList());
    }
}
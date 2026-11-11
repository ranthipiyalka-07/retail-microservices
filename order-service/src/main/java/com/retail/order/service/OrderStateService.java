package com.retail.order.service;

import com.retail.common.event.OrderCancelledEvent;
import com.retail.common.event.OrderCreatedEvent;
import com.retail.order.domain.*;
import com.retail.order.dto.AddressInput;
import com.retail.order.repository.CustomerOrderRepository;
import com.retail.order.repository.OrderStatusHistoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderStateService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Created(CustomerOrder order, boolean isNew) {}

    private final CustomerOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ApplicationEventPublisher events;

    public OrderStateService(CustomerOrderRepository orders, OrderStatusHistoryRepository history,
                             ApplicationEventPublisher events) {
        this.orders = orders;
        this.history = history;
        this.events = events;
    }

    // ---------- reads (scalar fields only: the entities are detached afterwards) ----------

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> find(Long orderId) {
        return orders.findById(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findByKey(Long customerId, String idempotencyKey) {
        return orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findOwned(Long customerId, Long orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> stalePending(Instant cutoff, int limit) {
        return orders.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING, cutoff, PageRequest.of(0, limit, Sort.by("id")));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> needingCompensation(Instant cutoff, int maxAttempts, int limit) {
        return orders.findNeedingCompensation(maxAttempts, cutoff, PageRequest.of(0, limit));
    }

    // ---------- state changes ----------

    /** Returns the existing order for a known idempotency key. A duplicate-key error means a parallel request won. */
    @Transactional
    public Created createPending(NewOrder cmd) {
        Optional<CustomerOrder> existing = orders.findByCustomerIdAndIdempotencyKey(cmd.customerId(), cmd.idempotencyKey());
        if (existing.isPresent()) {
            return new Created(existing.get(), false);
        }

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(newOrderNumber());
        order.setCustomerId(cmd.customerId());
        order.setCustomerEmail(cmd.customerEmail());
        order.setCartId(cmd.cartId());
        order.setIdempotencyKey(cmd.idempotencyKey());
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(cmd.currency());
        order.setSubtotal(cmd.totals().subtotal());
        order.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        order.setTaxTotal(cmd.totals().tax());
        order.setShippingFee(cmd.totals().shipping());
        order.setGrandTotal(cmd.totals().grandTotal());
        order.setPlacedAt(Instant.now());
        order.setDiscountTotal(cmd.totals().discount());

        for (PricedLine line : cmd.lines()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(line.productId());
            item.setSku(line.sku());
            item.setProductName(line.name());
            item.setUnitPrice(line.unitPrice());
            item.setDiscountAmount(BigDecimal.ZERO.setScale(2));
            item.setQuantity(line.quantity());
            item.setLineTotal(line.lineTotal());
            item.setDiscountAmount(line.lineDiscount());
            order.getItems().add(item);
        }
        order.getAddresses().add(address(order, AddressType.SHIPPING, cmd.shipping()));
        order.getAddresses().add(address(order, AddressType.BILLING, cmd.billing() != null ? cmd.billing() : cmd.shipping()));

        orders.saveAndFlush(order);
        history.save(new OrderStatusHistory(order.getId(), null, OrderStatus.PENDING, "Order created", "customer"));
        return new Created(order, true);
    }

    @Transactional
    public void recordPayment(Long orderId, Long paymentId) {
        orders.recordPayment(orderId, paymentId);
    }

    /** PENDING to CONFIRMED. False when somebody else moved the order first. */
    @Transactional
    public boolean confirm(Long orderId) {
        if (orders.confirm(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED, Instant.now()) == 0) {
            return false;
        }
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        history.save(new OrderStatusHistory(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED, "Payment confirmed", "system"));
        events.publishEvent(new OrderCreatedEvent(UUID.randomUUID(), Instant.now(), order.getId(), order.getOrderNumber(),
                order.getCustomerId(), order.getCustomerEmail(), order.getGrandTotal(), order.getCurrency(),
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                        .toList()));
        return true;
    }

    /** PENDING to FAILED or PAYMENT_FAILED, together with the follow-up work that is now due. */
    @Transactional
    public boolean fail(Long orderId, OrderStatus to, String reason, boolean refundDue, boolean stockReturnDue) {
        String clipped = reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
        if (orders.fail(orderId, OrderStatus.PENDING, to, clipped, refundDue, stockReturnDue, Instant.now()) == 0) {
            return false;
        }
        history.save(new OrderStatusHistory(orderId, OrderStatus.PENDING, to, clipped, "system"));
        return true;
    }

    /** CONFIRMED to CANCELLED. The refund and the stock return become due in the same update. */
    @Transactional
    public boolean cancel(Long orderId, String reason) {
        if (orders.cancel(orderId, OrderStatus.CONFIRMED, OrderStatus.CANCELLED, Instant.now()) == 0) {
            return false;
        }
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        history.save(new OrderStatusHistory(orderId, OrderStatus.CONFIRMED, OrderStatus.CANCELLED, reason, "customer"));
        events.publishEvent(new OrderCancelledEvent(UUID.randomUUID(), Instant.now(), order.getId(), order.getOrderNumber(),
                order.getCustomerId(), reason,
                order.getItems().stream().map(i -> new OrderCancelledEvent.Item(i.getProductId(), i.getQuantity())).toList()));
        return true;
    }

    @Transactional
    public void clear(Long orderId, DueFlag flag) {
        switch (flag) {
            case REFUND -> orders.clearRefundDue(orderId);
            case STOCK_RETURN -> orders.clearStockReturnDue(orderId);
            case CART_CHECKOUT -> orders.clearCartCheckoutDue(orderId);
        }
    }

    @Transactional
    public void recordCompensationFailure(Long orderId) {
        orders.bumpAttempts(orderId, Instant.now());
    }

    // ---------- helpers ----------

    private static OrderAddress address(CustomerOrder order, AddressType type, AddressInput in) {
        OrderAddress a = new OrderAddress();
        a.setOrder(order);
        a.setType(type);
        a.setName(in.name().trim());
        a.setLine1(in.line1().trim());
        a.setLine2(in.line2() == null || in.line2().isBlank() ? null : in.line2().trim());
        a.setCity(in.city().trim());
        a.setState(in.state() == null || in.state().isBlank() ? null : in.state().trim());
        a.setPostalCode(in.postalCode().trim());
        a.setCountry(in.country().trim().toUpperCase(Locale.ROOT));
        a.setPhone(in.phone() == null || in.phone().isBlank() ? null : in.phone().trim());
        return a;
    }

    /** ORD-20261109-7K3QXM2P: easy to read out loud (no 0, O, 1, I). */
    private static String newOrderNumber() {
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return "ORD-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }
}
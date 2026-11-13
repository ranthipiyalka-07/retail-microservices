package com.retail.order.service;

import com.retail.common.audit.Audit;
import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.OrderStatusChangedEvent;
import com.retail.order.domain.CustomerOrder;
import com.retail.order.domain.OrderStatus;
import com.retail.order.domain.OrderStatusHistory;
import com.retail.order.domain.Shipment;
import com.retail.order.dto.OrderResponse;
import com.retail.order.dto.UpdateStatusRequest;
import com.retail.order.exception.ConflictException;
import com.retail.order.exception.InvalidRequestException;
import com.retail.order.repository.CustomerOrderRepository;
import com.retail.order.repository.OrderStatusHistoryRepository;
import com.retail.order.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Staff moves a confirmed order along: PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED. */
@Service
public class FulfilmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentService.class);

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PACKED),
            OrderStatus.PACKED, Set.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED),
            OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED));

    private final CustomerOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ShipmentRepository shipments;
    private final OrderAssembler assembler;
    private final ApplicationEventPublisher events;

    public FulfilmentService(CustomerOrderRepository orders, OrderStatusHistoryRepository history,
                             ShipmentRepository shipments, OrderAssembler assembler, ApplicationEventPublisher events) {
        this.orders = orders;
        this.history = history;
        this.shipments = shipments;
        this.assembler = assembler;
        this.events = events;
    }

    @Transactional
    public OrderResponse update(Long orderId, UpdateStatusRequest request, String actor) {
        OrderStatus to = request.status();
        boolean staffTarget = to == OrderStatus.PACKED || to == OrderStatus.SHIPPED
                || to == OrderStatus.OUT_FOR_DELIVERY || to == OrderStatus.DELIVERED;
        if (!staffTarget) {
            throw new InvalidRequestException("status must be PACKED, SHIPPED, OUT_FOR_DELIVERY or DELIVERED");
        }
        if (to == OrderStatus.SHIPPED && (isBlank(request.carrier()) || isBlank(request.trackingNumber()))) {
            throw new InvalidRequestException("carrier and trackingNumber are required to ship an order");
        }

        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        OrderStatus from = order.getStatus();
        if (!NEXT.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException("An order that is " + from + " cannot be changed to " + to);
        }

        Instant now = Instant.now();
        if (orders.advance(orderId, from, to, now) == 0) {
            throw new ConflictException("The order was changed at the same time. Please reload it and try again.");
        }

        if (to == OrderStatus.SHIPPED) {
            shipments.save(new Shipment(orderId, request.carrier().trim(), request.trackingNumber().trim(),
                    isBlank(request.trackingUrl()) ? null : request.trackingUrl().trim(), now, request.estimatedDelivery()));
        } else if (to == OrderStatus.DELIVERED) {
            shipments.findByOrderId(orderId).ifPresent(s -> s.setDeliveredAt(now));
        }
        String comment = isBlank(request.comment()) ? "Status changed to " + to : request.comment().trim();
        history.save(new OrderStatusHistory(orderId, from, to, comment, actor));

        OrderStatusChangedEvent.TrackingInfo tracking = shipments.findByOrderId(orderId)
                .map(s -> new OrderStatusChangedEvent.TrackingInfo(s.getCarrier(), s.getTrackingNumber(),
                        s.getTrackingUrl(), s.getEstimatedDelivery()))
                .orElse(null);
        events.publishEvent(new OrderStatusChangedEvent(UUID.randomUUID(), now, orderId, order.getOrderNumber(),
                order.getCustomerId(), order.getCustomerEmail(), from.name(), to.name(), tracking));
        log.info("Order status changed: orderId={}, {} -> {}, by={}", orderId, from, to, actor);
        Audit.record("order.status.change", Audit.Outcome.SUCCESS, "order", orderId, from + " -> " + to);

        return assembler.detail(orders.findById(orderId).orElseThrow());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
package com.retail.order.service;

import com.retail.order.domain.*;
import com.retail.order.dto.*;
import com.retail.order.repository.OrderStatusHistoryRepository;
import com.retail.order.repository.ShipmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps entities to response objects. Call it inside a transaction: items and addresses load lazily. */
@Component
public class OrderAssembler {

    private final OrderStatusHistoryRepository history;
    private final ShipmentRepository shipments;

    public OrderAssembler(OrderStatusHistoryRepository history, ShipmentRepository shipments) {
        this.history = history;
        this.shipments = shipments;
    }

    public OrderResponse detail(CustomerOrder o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getSku(), i.getProductName(),
                        i.getUnitPrice(), i.getDiscountAmount(), i.getQuantity(), i.getLineTotal()))
                .toList();
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getSubtotal(), o.getDiscountTotal(),
                o.getTaxTotal(), o.getShippingFee(), o.getGrandTotal(), o.getCurrency(), o.getFailureReason(),
                o.getPlacedAt(), items, address(o, AddressType.SHIPPING), address(o, AddressType.BILLING),
                shipment(o.getId()), timeline(o.getId()));
    }

    public OrderSummary summary(CustomerOrder o) {
        int itemCount = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummary(o.getId(), o.getOrderNumber(), o.getCustomerId(), o.getStatus(),
                o.getGrandTotal(), o.getCurrency(), o.getPlacedAt(), itemCount);
    }

    public TrackingResponse tracking(CustomerOrder o) {
        return new TrackingResponse(o.getOrderNumber(), o.getStatus(), shipment(o.getId()), timeline(o.getId()));
    }

    private AddressResponse address(CustomerOrder o, AddressType type) {
        return o.getAddresses().stream().filter(a -> a.getType() == type).findFirst()
                .map(a -> new AddressResponse(a.getName(), a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                        a.getPostalCode(), a.getCountry(), a.getPhone()))
                .orElse(null);
    }

    private ShipmentResponse shipment(Long orderId) {
        return shipments.findByOrderId(orderId)
                .map(s -> new ShipmentResponse(s.getCarrier(), s.getTrackingNumber(), s.getTrackingUrl(),
                        s.getShippedAt(), s.getEstimatedDelivery(), s.getDeliveredAt()))
                .orElse(null);
    }

    private List<StatusEntry> timeline(Long orderId) {
        return history.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(h -> new StatusEntry(h.getFromStatus(), h.getToStatus(), h.getComment(), h.getChangedAt()))
                .toList();
    }
}
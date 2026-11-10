package com.retail.order.web;

import com.retail.order.domain.OrderStatus;
import com.retail.order.dto.PurchaseResponse;
import com.retail.order.repository.CustomerOrderRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    /** Paid and not cancelled. The caller decides which of these statuses count as "received". */
    private static final Set<OrderStatus> PURCHASED = Set.of(OrderStatus.CONFIRMED, OrderStatus.PACKED,
            OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private final CustomerOrderRepository orders;

    public InternalOrderController(CustomerOrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping("/purchases")
    public List<PurchaseResponse> purchases(@RequestParam Long customerId, @RequestParam Long productId) {
        return orders.findPurchases(customerId, productId, PURCHASED);
    }
}
package com.retail.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/internal/orders/purchases")
    List<PurchaseData> purchases(@RequestHeader("X-Internal-Key") String key,
                                 @RequestParam("customerId") Long customerId,
                                 @RequestParam("productId") Long productId);

    /** status is kept as text on purpose: a new order status in order-service must not break this service. */
    record PurchaseData(Long orderId, String status) {}
}
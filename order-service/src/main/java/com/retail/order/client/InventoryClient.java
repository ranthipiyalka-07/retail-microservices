package com.retail.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/internal/inventory/reservations")
    void reserve(@RequestHeader("X-Internal-Key") String key, @RequestBody ReserveRequest request);

    @PostMapping("/internal/inventory/reservations/{orderId}/commit")
    void commit(@RequestHeader("X-Internal-Key") String key, @PathVariable("orderId") Long orderId);

    @PostMapping("/internal/inventory/reservations/{orderId}/release")
    void release(@RequestHeader("X-Internal-Key") String key, @PathVariable("orderId") Long orderId);

    record ReserveRequest(Long orderId, List<Item> items) {
        public record Item(Long productId, int quantity) {}
    }
}
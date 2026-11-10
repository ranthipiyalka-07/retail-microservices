package com.retail.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "cart-service")
public interface CartClient {

    @GetMapping("/internal/cart/customers/{customerId}")
    CartData getCart(@RequestHeader("X-Internal-Key") String key, @PathVariable("customerId") Long customerId);

    @PostMapping("/internal/cart/customers/{customerId}/checkout")
    void checkout(@RequestHeader("X-Internal-Key") String key, @PathVariable("customerId") Long customerId,
                  @RequestParam("cartId") Long cartId);

    record CartData(Long cartId, List<Line> items) {
        public record Line(Long productId, String name, BigDecimal unitPrice, Integer quantity) {}
    }
}
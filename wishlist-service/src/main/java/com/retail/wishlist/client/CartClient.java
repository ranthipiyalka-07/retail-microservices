package com.retail.wishlist.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;

@FeignClient(name = "cart-service")
public interface CartClient {

    @PostMapping("/internal/cart/customers/{customerId}/items")
    CartData addItem(@RequestHeader("X-Internal-Key") String key, @PathVariable("customerId") Long customerId,
                     @RequestBody AddItem body);

    record AddItem(Long productId, int quantity) {}

    record CartData(Long cartId, Integer totalQuantity, BigDecimal subtotal) {}
}
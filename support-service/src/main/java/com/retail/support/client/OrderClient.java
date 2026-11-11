package com.retail.support.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service")
public interface OrderClient {

    /** 204 when the order belongs to the customer, 404 when it does not (or does not exist). */
    @GetMapping("/internal/orders/{orderId}/ownership")
    void ownership(@RequestHeader("X-Internal-Key") String key, @PathVariable("orderId") Long orderId,
                   @RequestParam("customerId") Long customerId);
}
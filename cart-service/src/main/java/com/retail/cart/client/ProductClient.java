package com.retail.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")          // resolved through Eureka
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")        // public endpoint: only ACTIVE products are returned
    ProductInfo getProduct(@PathVariable("id") Long id);
}
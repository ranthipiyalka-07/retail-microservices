package com.retail.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")           // public endpoint: only ACTIVE products are returned
    ProductData getProduct(@PathVariable("id") Long id);

    record ProductData(Long id, String sku, String name, Long categoryId, BigDecimal basePrice) {}
}
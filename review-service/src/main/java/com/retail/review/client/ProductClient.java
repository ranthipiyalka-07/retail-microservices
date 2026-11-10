package com.retail.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;

@FeignClient(name = "product-service")
public interface ProductClient {

    @PutMapping("/internal/products/{id}/rating")
    void updateRating(@RequestHeader("X-Internal-Key") String key, @PathVariable("id") Long id,
                      @RequestBody RatingUpdate body);

    record RatingUpdate(BigDecimal averageRating, int reviewCount, long version) {}
}
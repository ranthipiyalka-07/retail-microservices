package com.retail.wishlist.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/internal/products/summaries")
    List<ProductData> summaries(@RequestHeader("X-Internal-Key") String key, @RequestParam("ids") List<Long> ids);

    /** Only the fields this service shows. Unknown JSON fields are ignored. */
    record ProductData(Long id, String name, String brand, BigDecimal basePrice, BigDecimal effectivePrice,
                       BigDecimal discountAmount, String campaignName, BigDecimal avgRating, Integer reviewCount,
                       String primaryImageUrl) {}
}
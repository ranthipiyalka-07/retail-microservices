package com.retail.product.dto;

import java.math.BigDecimal;

public record ProductSummary(Long id, String sku, String name, String brand, Long categoryId,
                             BigDecimal basePrice, BigDecimal effectivePrice, BigDecimal discountAmount,
                             String campaignName, BigDecimal avgRating, int reviewCount, String primaryImageUrl) {

    public ProductSummary withPrice(BigDecimal effectivePrice, BigDecimal discountAmount, String campaignName) {
        return new ProductSummary(id, sku, name, brand, categoryId, basePrice, effectivePrice, discountAmount,
                campaignName, avgRating, reviewCount, primaryImageUrl);
    }
}
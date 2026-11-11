package com.retail.product.dto;

import com.retail.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductDetail(Long id, String sku, String name, String description, String brand, Long categoryId,
                            BigDecimal basePrice, BigDecimal effectivePrice, BigDecimal discountAmount,
                            String campaignName, ProductStatus status, BigDecimal avgRating, int reviewCount,
                            List<ImageResponse> images, List<AttributeResponse> attributes, Instant createdAt) {

    public ProductDetail withPrice(BigDecimal effectivePrice, BigDecimal discountAmount, String campaignName) {
        return new ProductDetail(id, sku, name, description, brand, categoryId, basePrice, effectivePrice,
                discountAmount, campaignName, status, avgRating, reviewCount, images, attributes, createdAt);
    }
}
package com.retail.product.dto;

import java.math.BigDecimal;

public record ProductSummary(Long id, String sku, String name, String brand, Long categoryId,
                             BigDecimal basePrice, BigDecimal avgRating, int reviewCount,
                             String primaryImageUrl) {}
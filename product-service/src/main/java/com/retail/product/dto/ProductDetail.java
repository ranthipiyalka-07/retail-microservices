package com.retail.product.dto;

import com.retail.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductDetail(Long id, String sku, String name, String description, String brand, Long categoryId,
                            BigDecimal basePrice, ProductStatus status, BigDecimal avgRating, int reviewCount,
                            List<ImageResponse> images, List<AttributeResponse> attributes, Instant createdAt) {}
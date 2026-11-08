package com.retail.product.dto;

import com.retail.product.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain letters, digits, '.', '_' and '-' only") String sku,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 10000) String description,
        @Size(max = 100) String brand,
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        ProductStatus status,                                      // optional: ACTIVE (default) or INACTIVE
        @Size(max = 10) List<@Valid ImageRequest> images,
        @Size(max = 30) List<@Valid AttributeRequest> attributes) {}
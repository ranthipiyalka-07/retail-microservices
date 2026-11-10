package com.retail.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RatingUpdateRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("5.00") BigDecimal averageRating,
        @Min(0) int reviewCount,
        @Min(1) long version) {}
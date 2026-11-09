package com.retail.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(1) @Max(CartLimits.MAX_QUANTITY) Integer quantity) {}
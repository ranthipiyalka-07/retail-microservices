package com.retail.campaign.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** The caller knows the catalog: it sends the base price and the category. This service never asks product-service. */
public record PriceQueryItem(
        @NotNull @Positive Long productId,
        @Positive Long categoryId,                                  // optional: without it only product targets match
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal basePrice) {}
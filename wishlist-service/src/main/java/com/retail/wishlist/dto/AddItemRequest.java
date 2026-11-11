package com.retail.wishlist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddItemRequest(@NotNull @Positive Long productId) {}
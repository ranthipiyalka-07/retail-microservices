package com.retail.wishlist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** The whole body is optional: no body means quantity 1. */
public record MoveToCartRequest(@Min(1) @Max(99) Integer quantity) {}
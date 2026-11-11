package com.retail.wishlist.dto;

import java.math.BigDecimal;

public record MoveToCartResponse(Long productId, int quantity, int cartTotalQuantity, BigDecimal cartSubtotal) {}
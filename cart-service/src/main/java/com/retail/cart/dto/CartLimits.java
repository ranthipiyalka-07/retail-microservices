package com.retail.cart.dto;

public final class CartLimits {
    private CartLimits() {}
    public static final int MAX_QUANTITY = 99;          // per line
    public static final int MAX_DISTINCT_ITEMS = 50;    // lines per cart
}
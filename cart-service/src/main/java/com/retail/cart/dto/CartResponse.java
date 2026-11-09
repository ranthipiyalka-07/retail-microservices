package com.retail.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/** guestToken is set for guest carts only (the secret the client must send back as X-Cart-Token). */
public record CartResponse(Long cartId, String guestToken, List<CartItemResponse> items,
                           int totalQuantity, BigDecimal subtotal) {

    public static CartResponse empty() {
        return new CartResponse(null, null, List.of(), 0, BigDecimal.ZERO.setScale(2));
    }
}
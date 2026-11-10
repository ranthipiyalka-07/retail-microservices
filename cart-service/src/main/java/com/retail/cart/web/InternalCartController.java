package com.retail.cart.web;

import com.retail.cart.dto.CartResponse;
import com.retail.cart.service.CartOwner;
import com.retail.cart.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/cart")
public class InternalCartController {

    private final CartService cartService;

    public InternalCartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** An empty cart comes back with cartId null and no items. */
    @GetMapping("/customers/{customerId}")
    public CartResponse get(@PathVariable Long customerId) {
        return cartService.view(CartOwner.customer(customerId));
    }

    /** Idempotent: always 204, even when the cart was already closed. */
    @PostMapping("/customers/{customerId}/checkout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkout(@PathVariable Long customerId, @RequestParam Long cartId) {
        cartService.markCheckedOut(customerId, cartId);
    }
}
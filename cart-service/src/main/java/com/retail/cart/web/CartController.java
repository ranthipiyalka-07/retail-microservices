package com.retail.cart.web;

import com.retail.cart.dto.AddItemRequest;
import com.retail.cart.dto.CartResponse;
import com.retail.cart.dto.UpdateQuantityRequest;
import com.retail.cart.exception.InvalidRequestException;
import com.retail.cart.service.CartOwner;
import com.retail.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private static final String TOKEN_HEADER = "X-Cart-Token";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> get(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return respond(cartService.view(owner(token)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> add(@Valid @RequestBody AddItemRequest request,
                                            @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return respond(cartService.addItem(owner(token), request.productId(), request.quantity()));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> update(@PathVariable Long itemId,
                                               @Valid @RequestBody UpdateQuantityRequest request,
                                               @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return respond(cartService.updateQuantity(owner(token), itemId, request.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> remove(@PathVariable Long itemId,
                                               @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return respond(cartService.removeItem(owner(token), itemId));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        cartService.clear(owner(token));
    }

    /** Needs a customer JWT (enforced by SecurityConfig) and the guest cart's token. */
    @PostMapping("/merge")
    public ResponseEntity<CartResponse> merge(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        String guestToken = validToken(token);
        if (guestToken == null) {
            throw new InvalidRequestException("Header " + TOKEN_HEADER + " is required");
        }
        CartOwner owner = owner(null);
        return respond(cartService.merge(owner.customerId(), guestToken));
    }

    /** Customer (JWT with customerId) wins over guest. A staff account has no cart at all. */
    private static CartOwner owner(String token) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaim("customerId");
            if (claim instanceof Number number) {
                return CartOwner.customer(number.longValue());
            }
            throw new AccessDeniedException("Not a customer account");
        }
        return CartOwner.guest(validToken(token));
    }

    private static String validToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        if (!TOKEN_FORMAT.matcher(trimmed).matches()) {
            throw new InvalidRequestException("Invalid " + TOKEN_HEADER + " header");
        }
        return trimmed;
    }

    private static ResponseEntity<CartResponse> respond(CartResponse cart) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (cart.guestToken() != null) {
            builder.header(TOKEN_HEADER, cart.guestToken());
        }
        return builder.body(cart);
    }
}
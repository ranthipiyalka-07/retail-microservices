package com.retail.wishlist.web;

import com.retail.wishlist.dto.*;
import com.retail.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public WishlistResponse view(@RequestParam(required = false) Integer page,
                                 @RequestParam(required = false) Integer size) {
        return wishlistService.view(CurrentCustomer.id(), page, size);
    }

    @PutMapping
    public WishlistInfo rename(@Valid @RequestBody RenameRequest request) {
        return wishlistService.rename(CurrentCustomer.id(), request.name());
    }

    /** 201 when the product was added, 200 when it was already on the list. */
    @PostMapping("/items")
    public ResponseEntity<WishlistItemResponse> add(@Valid @RequestBody AddItemRequest request) {
        WishlistService.AddResult result = wishlistService.add(CurrentCustomer.id(), request.productId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long productId) {
        wishlistService.remove(CurrentCustomer.id(), productId);
    }

    @PostMapping("/items/{productId}/move-to-cart")
    public MoveToCartResponse moveToCart(@PathVariable Long productId,
                                         @Valid @RequestBody(required = false) MoveToCartRequest request) {
        return wishlistService.moveToCart(CurrentCustomer.id(), productId, request == null ? null : request.quantity());
    }
}
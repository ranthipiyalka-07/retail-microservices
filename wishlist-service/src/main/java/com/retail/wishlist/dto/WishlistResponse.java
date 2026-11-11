package com.retail.wishlist.dto;

import java.util.List;

/** id is null for a customer who has no wishlist yet. */
public record WishlistResponse(Long id, String name, List<WishlistItemResponse> items, int page, int size,
                               long totalElements, int totalPages) {

    public static WishlistResponse empty(String name, int page, int size) {
        return new WishlistResponse(null, name, List.of(), page, size, 0, 0);
    }
}
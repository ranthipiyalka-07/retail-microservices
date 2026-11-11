package com.retail.wishlist.dto;

import java.time.Instant;

/** available = false: the product is inactive, deleted or unknown. product is then null. */
public record WishlistItemResponse(Long productId, Instant addedAt, boolean available, ProductCard product) {}
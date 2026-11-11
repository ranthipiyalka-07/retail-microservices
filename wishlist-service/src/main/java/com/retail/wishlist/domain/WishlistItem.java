package com.retail.wishlist.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "wishlist_items")
@Getter @Setter
public class WishlistItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wishlist_id", nullable = false, updatable = false)
    private Long wishlistId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;
}
package com.retail.wishlist.repository;

import com.retail.wishlist.domain.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Page<WishlistItem> findByWishlistId(Long wishlistId, Pageable pageable);

    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);

    long countByWishlistId(Long wishlistId);

    /** Returns how many rows were removed: 0 is fine, the item was not there. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WishlistItem i where i.wishlistId = :wishlistId and i.productId = :productId")
    int deleteItem(@Param("wishlistId") Long wishlistId, @Param("productId") Long productId);
}
package com.retail.cart.repository;

import com.retail.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);       // ownership check: the id must be in YOUR cart

    long countByCartId(Long cartId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem i where i.cartId = :cartId")
    int deleteAllByCartId(@Param("cartId") Long cartId);
}
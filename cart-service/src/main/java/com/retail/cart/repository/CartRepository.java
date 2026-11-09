package com.retail.cart.repository;

import com.retail.cart.domain.Cart;
import com.retail.cart.domain.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByCustomerIdAndStatus(Long customerId, CartStatus status);

    Optional<Cart> findByGuestTokenAndStatus(String guestToken, CartStatus status);

    /** Atomic status change: returns 0 when the cart was not in the expected status any more. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Cart c set c.status = :to, c.updatedAt = :now where c.id = :id and c.status = :from")
    int transition(@Param("id") Long id, @Param("from") CartStatus from,
                   @Param("to") CartStatus to, @Param("now") Instant now);

    /** Whoever gets true owns the merge. A second, parallel merge of the same guest cart gets false. */
    default boolean claimForMerge(Long guestCartId) {
        return transition(guestCartId, CartStatus.ACTIVE, CartStatus.MERGED, Instant.now()) == 1;
    }

    @Modifying
    @Query("delete from Cart c where c.guestToken is not null and c.status = :active and c.expiresAt < :now")
    int deleteExpiredGuestCarts(@Param("active") CartStatus active, @Param("now") Instant now);

    @Modifying
    @Query("delete from Cart c where c.status = :status and c.updatedAt < :cutoff")
    int deleteByStatusOlderThan(@Param("status") CartStatus status, @Param("cutoff") Instant cutoff);
}
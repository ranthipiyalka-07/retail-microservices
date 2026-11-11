package com.retail.wishlist.repository;

import com.retail.wishlist.domain.Wishlist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByCustomerId(Long customerId);

    boolean existsByCustomerId(Long customerId);

    /** SELECT ... FOR UPDATE: changes to one customer's wishlist are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wishlist w where w.customerId = :customerId")
    Optional<Wishlist> findByCustomerIdForUpdate(@Param("customerId") Long customerId);
}
package com.retail.inventory.repository;

import com.retail.inventory.domain.ReservationStatus;
import com.retail.inventory.domain.StockReservation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderIdOrderByProductIdAsc(Long orderId);

    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant cutoff, Pageable pageable);

    /** Atomic status change: 0 means somebody else changed the reservation first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockReservation r set r.status = :to, r.updatedAt = :now where r.id = :id and r.status = :from")
    int transition(@Param("id") Long id, @Param("from") ReservationStatus from,
                   @Param("to") ReservationStatus to, @Param("now") Instant now);

    /** Same, but only when the reservation is really past its expiry time. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockReservation r set r.status = :to, r.updatedAt = :now "
            + "where r.id = :id and r.status = :from and r.expiresAt < :now")
    int transitionIfExpired(@Param("id") Long id, @Param("from") ReservationStatus from,
                            @Param("to") ReservationStatus to, @Param("now") Instant now);
}
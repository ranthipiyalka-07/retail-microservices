package com.retail.payment.repository;

import com.retail.payment.domain.Payment;
import com.retail.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    /** SELECT ... FOR UPDATE: settling and refunding one payment can never interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findLockedById(@Param("id") Long id);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff, Pageable pageable);
}
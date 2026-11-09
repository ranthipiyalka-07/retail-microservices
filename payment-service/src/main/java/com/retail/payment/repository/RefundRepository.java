package com.retail.payment.repository;

import com.retail.payment.domain.Refund;
import com.retail.payment.domain.RefundStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    List<Refund> findByStatusAndCreatedAtBefore(RefundStatus status, Instant cutoff, Pageable pageable);

    /** Null when there is no matching refund: callers treat that as zero. */
    @Query("select sum(r.amount) from Refund r where r.paymentId = :paymentId and r.status in :statuses")
    BigDecimal sumAmount(@Param("paymentId") Long paymentId, @Param("statuses") Collection<RefundStatus> statuses);
}
package com.retail.support.repository;

import com.retail.support.domain.SupportTicket;
import com.retail.support.domain.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

    Optional<SupportTicket> findByIdAndCustomerId(Long id, Long customerId);

    Optional<SupportTicket> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Page<SupportTicket> findByCustomerId(Long customerId, Pageable pageable);

    Page<SupportTicket> findByCustomerIdAndStatus(Long customerId, TicketStatus status, Pageable pageable);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<TicketStatus> statuses);

    /** SELECT ... FOR UPDATE: changes to one ticket (and its messages) are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id = :id and t.customerId = :customerId")
    Optional<SupportTicket> findOwnedForUpdate(@Param("id") Long id, @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findForUpdate(@Param("id") Long id);

    @Query("select t.id from SupportTicket t where t.status = :status and t.resolvedAt < :cutoff order by t.id")
    List<Long> findIdsResolvedBefore(@Param("status") TicketStatus status, @Param("cutoff") Instant cutoff,
                                     Pageable pageable);

    /**
     * RESOLVED to CLOSED, only while the ticket is still resolved and old enough. A customer reply that reopens the
     * ticket at the same moment wins: this update then changes nothing (0).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupportTicket t set t.status = :to, t.closedAt = :now, t.updatedAt = :now "
            + "where t.id = :id and t.status = :from and t.resolvedAt < :cutoff")
    int closeIfResolvedBefore(@Param("id") Long id, @Param("from") TicketStatus from, @Param("to") TicketStatus to,
                              @Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
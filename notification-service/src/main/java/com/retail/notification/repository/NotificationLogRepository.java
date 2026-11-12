package com.retail.notification.repository;

import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByEventIdAndRecipientEmail(String eventId, String recipientEmail);

    Page<NotificationLog> findByStatus(NotificationStatus status, Pageable pageable);

    /** Emails that are due and not under a live lease. */
    @Query("select n.id from NotificationLog n where n.status = :status and n.nextAttemptAt <= :now "
            + "and (n.lockedUntil is null or n.lockedUntil < :now) order by n.id")
    List<Long> findDueIds(@Param("status") NotificationStatus status, @Param("now") Instant now, Pageable pageable);

    /** The claim: only one instance gets 1. It counts the attempt and sets a lease. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationLog n set n.lockedUntil = :until, n.attempts = n.attempts + 1, n.updatedAt = :now "
            + "where n.id = :id and n.status = :pending and n.nextAttemptAt <= :now "
            + "and (n.lockedUntil is null or n.lockedUntil < :now)")
    int claim(@Param("id") Long id, @Param("pending") NotificationStatus pending,
              @Param("until") Instant until, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationLog n set n.status = :sent, n.sentAt = :now, n.lockedUntil = null, n.lastError = null, "
            + "n.updatedAt = :now where n.id = :id and n.status = :pending")
    int markSent(@Param("id") Long id, @Param("pending") NotificationStatus pending,
                 @Param("sent") NotificationStatus sent, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationLog n set n.status = :failed, n.lastError = :error, n.lockedUntil = null, "
            + "n.updatedAt = :now where n.id = :id and n.status = :pending")
    int markFailed(@Param("id") Long id, @Param("pending") NotificationStatus pending,
                   @Param("failed") NotificationStatus failed, @Param("error") String error, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationLog n set n.nextAttemptAt = :next, n.lastError = :error, n.lockedUntil = null, "
            + "n.updatedAt = :now where n.id = :id and n.status = :pending")
    int reschedule(@Param("id") Long id, @Param("pending") NotificationStatus pending,
                   @Param("next") Instant next, @Param("error") String error, @Param("now") Instant now);

    /** Admin retry: only a FAILED email goes back to PENDING, with a fresh set of attempts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationLog n set n.status = :pending, n.attempts = 0, n.nextAttemptAt = :now, "
            + "n.lockedUntil = null, n.lastError = null, n.updatedAt = :now where n.id = :id and n.status = :failed")
    int retry(@Param("id") Long id, @Param("failed") NotificationStatus failed,
              @Param("pending") NotificationStatus pending, @Param("now") Instant now);

    @Modifying
    @Query("delete from NotificationLog n where n.status in :statuses and n.createdAt < :cutoff")
    int deleteOlderThan(@Param("statuses") Collection<NotificationStatus> statuses, @Param("cutoff") Instant cutoff);
}
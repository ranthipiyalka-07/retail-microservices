package com.retail.notification.dto;

import com.retail.notification.domain.NotificationStatus;

import java.time.Instant;

/** The detail view includes the email text, which is personal data: admins only. */
public record NotificationDetail(Long id, String eventId, String eventType, String recipient, String templateCode,
                                 String subject, String body, NotificationStatus status, int attempts,
                                 String lastError, String referenceType, String referenceId, Instant createdAt,
                                 Instant sentAt, Instant nextAttemptAt) {}
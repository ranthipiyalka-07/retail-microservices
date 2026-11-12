package com.retail.notification.dto;

import com.retail.notification.domain.NotificationStatus;

import java.time.Instant;

public record NotificationSummary(Long id, String eventType, String recipient, String templateCode, String subject,
                                  NotificationStatus status, int attempts, String lastError, String referenceType,
                                  String referenceId, Instant createdAt, Instant sentAt, Instant nextAttemptAt) {}
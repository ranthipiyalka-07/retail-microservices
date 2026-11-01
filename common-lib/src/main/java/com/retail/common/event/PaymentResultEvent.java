package com.retail.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResultEvent(
        UUID eventId, Instant occurredAt,
        Long orderId, Long paymentId,
        Status status, BigDecimal amount, String currency,
        String failureReason) implements DomainEvent {            // failureReason null on success

    public enum Status { SUCCESS, FAILED }
}
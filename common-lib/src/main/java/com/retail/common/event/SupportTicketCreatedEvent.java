package com.retail.common.event;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketCreatedEvent(
        UUID eventId, Instant occurredAt,
        String ticketNumber, Long customerId, String customerEmail,
        String subject) implements DomainEvent {}
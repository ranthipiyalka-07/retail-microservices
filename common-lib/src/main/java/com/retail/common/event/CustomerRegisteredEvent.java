package com.retail.common.event;

import java.time.Instant;
import java.util.UUID;

public record CustomerRegisteredEvent(UUID eventId, Instant occurredAt,
                                      Long customerId, String email, String firstName) implements DomainEvent {}
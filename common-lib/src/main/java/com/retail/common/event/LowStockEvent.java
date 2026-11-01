package com.retail.common.event;

import java.time.Instant;
import java.util.UUID;

public record LowStockEvent(
        UUID eventId, Instant occurredAt,
        Long productId, int quantityAvailable, int reorderLevel) implements DomainEvent {}
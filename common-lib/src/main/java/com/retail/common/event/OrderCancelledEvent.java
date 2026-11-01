package com.retail.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId, Instant occurredAt,
        Long orderId, String orderNumber, Long customerId,
        String reason, List<Item> items) implements DomainEvent {   // inventory releases stock; payment refunds

    public record Item(Long productId, int quantity) {}
}
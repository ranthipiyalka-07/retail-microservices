package com.retail.common.event;

import java.time.Instant;
import java.util.UUID;

public record ReviewPublishedEvent(
        UUID eventId, Instant occurredAt,
        Long reviewId, Long productId, Long customerId,
        int rating) implements DomainEvent {}                     // product-service updates avg_rating and review_count
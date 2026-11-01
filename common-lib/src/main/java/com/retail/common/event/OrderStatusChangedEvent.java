package com.retail.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID eventId, Instant occurredAt,
        Long orderId, String orderNumber,
        Long customerId, String customerEmail,
        String oldStatus, String newStatus,
        TrackingInfo trackingInfo) implements DomainEvent {   // trackingInfo is null until shipped

    public record TrackingInfo(String carrier, String trackingNumber, String trackingUrl,
                               Instant estimatedDelivery) {}
}
package com.retail.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignChangedEvent(
        UUID eventId, Instant occurredAt,
        Long campaignId, ChangeType changeType,
        List<Long> productIds) implements DomainEvent {           // product-service evicts cached prices for these

    public enum ChangeType { ACTIVATED, EXPIRED }
}
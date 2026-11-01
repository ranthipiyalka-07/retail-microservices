package com.retail.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId, Instant occurredAt,
        Long orderId, String orderNumber,
        Long customerId, String customerEmail,
        BigDecimal grandTotal, String currency,
        List<Item> items) implements DomainEvent {

    public record Item(Long productId, int quantity, BigDecimal unitPrice) {}
}
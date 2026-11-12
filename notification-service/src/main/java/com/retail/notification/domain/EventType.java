package com.retail.notification.domain;

import com.retail.common.event.*;

import java.util.Optional;

/** The events this service turns into emails, and the class each one is read into. */
public enum EventType {
    CUSTOMER_REGISTERED(CustomerRegisteredEvent.class),
    ORDER_CREATED(OrderCreatedEvent.class),
    ORDER_STATUS_CHANGED(OrderStatusChangedEvent.class),
    SUPPORT_TICKET_CREATED(SupportTicketCreatedEvent.class),
    SUPPORT_TICKET_REPLIED(SupportTicketRepliedEvent.class),
    LOW_STOCK(LowStockEvent.class);

    private final Class<? extends DomainEvent> payloadType;

    EventType(Class<? extends DomainEvent> payloadType) {
        this.payloadType = payloadType;
    }

    public Class<? extends DomainEvent> payloadType() {
        return payloadType;
    }

    public static Optional<EventType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
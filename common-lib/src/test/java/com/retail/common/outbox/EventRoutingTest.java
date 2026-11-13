package com.retail.common.outbox;

import com.retail.common.event.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventRoutingTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private static void expect(DomainEvent event, String topic, String key) {
        EventRouting.Route route = EventRouting.route(event).orElseThrow();
        assertThat(route.topic()).isEqualTo(topic);
        assertThat(route.key()).isEqualTo(key);
    }

    @Test
    void everyEventGoesToItsTopicWithItsBusinessKey() {
        expect(new CustomerRegisteredEvent(ID, NOW, 3L, "a@b.com", "A"), Topics.CUSTOMER_REGISTERED, "3");
        expect(new OrderCreatedEvent(ID, NOW, 7L, "ORD-1", 3L, "a@b.com", BigDecimal.TEN, "USD", List.of()),
                Topics.ORDER_CREATED, "7");
        expect(new OrderStatusChangedEvent(ID, NOW, 7L, "ORD-1", 3L, "a@b.com", "PACKED", "SHIPPED", null),
                Topics.ORDER_STATUS_CHANGED, "7");
        expect(new OrderCancelledEvent(ID, NOW, 7L, "ORD-1", 3L, "why", List.of()), Topics.ORDER_CANCELLED, "7");
        expect(new ReviewPublishedEvent(ID, NOW, 1L, 9L, 3L, 5), Topics.REVIEW_PUBLISHED, "9");
        expect(new SupportTicketCreatedEvent(ID, NOW, "TKT-1", 3L, "a@b.com", "s"), Topics.SUPPORT_TICKET_CREATED, "TKT-1");
        expect(new SupportTicketRepliedEvent(ID, NOW, "TKT-1", 3L, "a@b.com", "s"), Topics.SUPPORT_TICKET_REPLIED, "TKT-1");
        expect(new LowStockEvent(ID, NOW, 2L, 3, 5), Topics.INVENTORY_LOW_STOCK, "2");
    }

    @Test
    void paymentAndCampaignEventsPickTheirTopicFromTheOutcome() {
        expect(new PaymentResultEvent(ID, NOW, 7L, 1L, PaymentResultEvent.Status.SUCCESS, BigDecimal.TEN, "USD", null),
                Topics.PAYMENT_COMPLETED, "7");
        expect(new PaymentResultEvent(ID, NOW, 7L, 1L, PaymentResultEvent.Status.FAILED, BigDecimal.TEN, "USD", "declined"),
                Topics.PAYMENT_FAILED, "7");
        expect(new CampaignChangedEvent(ID, NOW, 4L, CampaignChangedEvent.ChangeType.ACTIVATED, List.of(1L)),
                Topics.CAMPAIGN_ACTIVATED, "4");
        expect(new CampaignChangedEvent(ID, NOW, 4L, CampaignChangedEvent.ChangeType.EXPIRED, List.of(1L)),
                Topics.CAMPAIGN_EXPIRED, "4");
    }

    @Test
    void anUnknownEventHasNoRoute() {
        DomainEvent unknown = new DomainEvent() {
            @Override public UUID eventId() { return ID; }
            @Override public Instant occurredAt() { return NOW; }
        };

        assertThat(EventRouting.route(unknown)).isEmpty();
    }
}
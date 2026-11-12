package com.retail.notification.service;

import com.retail.common.event.*;
import com.retail.notification.config.NotificationProperties;
import com.retail.notification.domain.EventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationFactoryTest {

    private static NotificationProperties props(List<String> lowStockRecipients) {
        return new NotificationProperties("Retail Store", 4, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(2),
                List.of("SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"), lowStockRecipients, Duration.ofDays(90),
                new NotificationProperties.Mail("log", "no-reply@retail.local"));
    }

    private final NotificationFactory factory = new NotificationFactory(props(List.of("stock@retail.local", "boss@retail.local")));

    @Test
    void aRegistrationGetsAWelcomeEmailAndAMissingNameBecomesThere() {
        Draft named = factory.drafts(new CustomerRegisteredEvent(UUID.randomUUID(), Instant.now(), 3L,
                "john@example.com", "  John  ")).get(0);
        Draft unnamed = factory.drafts(new CustomerRegisteredEvent(UUID.randomUUID(), Instant.now(), 4L,
                "jane@example.com", null)).get(0);

        assertThat(named.templateCode()).isEqualTo("CUSTOMER_REGISTERED");
        assertThat(named.eventType()).isEqualTo(EventType.CUSTOMER_REGISTERED);
        assertThat(named.recipient()).isEqualTo("john@example.com");
        assertThat(named.vars()).containsEntry("firstName", "John").containsEntry("shopName", "Retail Store");
        assertThat(named.referenceType()).isEqualTo("CUSTOMER");
        assertThat(named.referenceId()).isEqualTo("3");
        assertThat(unnamed.vars()).containsEntry("firstName", "there");
    }

    @Test
    void anOrderConfirmationShowsTheNumberTheItemCountAndTheTotalWithTwoDecimals() {
        var event = new OrderCreatedEvent(UUID.randomUUID(), Instant.now(), 7L, "ORD-1", 3L, "john@example.com",
                new BigDecimal("75.9"), "USD", List.of(new OrderCreatedEvent.Item(1L, 2, new BigDecimal("19.99")),
                new OrderCreatedEvent.Item(2L, 1, new BigDecimal("24.50"))));

        Draft draft = factory.drafts(event).get(0);

        assertThat(draft.templateCode()).isEqualTo("ORDER_CONFIRMED");
        assertThat(draft.vars()).containsEntry("orderNumber", "ORD-1").containsEntry("itemCount", "3")
                .containsEntry("total", "75.90 USD");
        assertThat(draft.referenceType()).isEqualTo("ORDER");
        assertThat(draft.referenceId()).isEqualTo("7");
    }

    @Test
    void aShippedOrderGetsTheTrackingDetailsInOrder() {
        var tracking = new OrderStatusChangedEvent.TrackingInfo("DHL", "JD001", "https://example.com/track/JD001",
                Instant.parse("2026-11-14T00:00:00Z"));
        var event = new OrderStatusChangedEvent(UUID.randomUUID(), Instant.now(), 7L, "ORD-1", 3L, "john@example.com",
                "PACKED", "SHIPPED", tracking);

        Draft draft = factory.drafts(event).get(0);

        assertThat(draft.templateCode()).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(draft.vars().get("statusLabel")).isEqualTo("has been shipped");
        assertThat(draft.vars().get("trackingDetails")).isEqualTo("Carrier: DHL\nTracking number: JD001\n"
                + "Track your parcel: https://example.com/track/JD001\nEstimated delivery: 14 November 2026");
    }

    @Test
    void aDeliveredOrderHasNoTrackingTextAndOnlyTheConfiguredStatusesSendAnEmail() {
        var delivered = new OrderStatusChangedEvent(UUID.randomUUID(), Instant.now(), 7L, "ORD-1", 3L,
                "john@example.com", "SHIPPED", "DELIVERED", null);
        var packed = new OrderStatusChangedEvent(UUID.randomUUID(), Instant.now(), 7L, "ORD-1", 3L,
                "john@example.com", "CONFIRMED", "PACKED", null);

        assertThat(factory.drafts(delivered).get(0).vars()).containsEntry("trackingDetails", "")
                .containsEntry("statusLabel", "has been delivered");
        assertThat(factory.drafts(packed)).isEmpty();
    }

    @Test
    void supportEmailsUseTheCleanedOneLineSubject() {
        var created = new SupportTicketCreatedEvent(UUID.randomUUID(), Instant.now(), "TKT-1", 3L, "john@example.com",
                "Charged\r\ntwice  please");
        var replied = new SupportTicketRepliedEvent(UUID.randomUUID(), Instant.now(), "TKT-1", 3L, "john@example.com",
                "Charged twice");

        Draft first = factory.drafts(created).get(0);
        Draft second = factory.drafts(replied).get(0);

        assertThat(first.templateCode()).isEqualTo("SUPPORT_TICKET_CREATED");
        assertThat(first.vars()).containsEntry("subject", "Charged twice please").containsEntry("ticketNumber", "TKT-1");
        assertThat(first.referenceType()).isEqualTo("TICKET");
        assertThat(second.templateCode()).isEqualTo("SUPPORT_TICKET_REPLIED");
        assertThat(second.eventType()).isEqualTo(EventType.SUPPORT_TICKET_REPLIED);
    }

    @Test
    void lowStockGoesToEveryConfiguredRecipientAndToNobodyWhenThereAreNone() {
        var event = new LowStockEvent(UUID.randomUUID(), Instant.now(), 2L, 3, 5);

        List<Draft> drafts = factory.drafts(event);

        assertThat(drafts).extracting(Draft::recipient).containsExactly("stock@retail.local", "boss@retail.local");
        assertThat(drafts.get(0).templateCode()).isEqualTo("LOW_STOCK_ALERT");
        assertThat(drafts.get(0).vars()).containsEntry("productId", "2").containsEntry("available", "3")
                .containsEntry("reorderLevel", "5");
        assertThat(new NotificationFactory(props(null)).drafts(event)).isEmpty();
    }

    @Test
    void anUnsupportedEventIsRefused() {
        DomainEvent unknown = new DomainEvent() {
            @Override public UUID eventId() { return UUID.randomUUID(); }
            @Override public Instant occurredAt() { return Instant.now(); }
        };

        assertThatThrownBy(() -> factory.drafts(unknown)).isInstanceOf(IllegalArgumentException.class);
    }
}
package com.retail.common.outbox;

import com.retail.common.event.*;

import java.util.Optional;

/** Which topic an event goes to, and which business id is the message key (same key = same partition = in order). */
public final class EventRouting {

    public record Route(String topic, String key) {}

    private EventRouting() {}

    public static Optional<Route> route(DomainEvent event) {
        if (event instanceof CustomerRegisteredEvent e) {
            return route(Topics.CUSTOMER_REGISTERED, e.customerId());
        }
        if (event instanceof OrderCreatedEvent e) {
            return route(Topics.ORDER_CREATED, e.orderId());
        }
        if (event instanceof OrderStatusChangedEvent e) {
            return route(Topics.ORDER_STATUS_CHANGED, e.orderId());
        }
        if (event instanceof OrderCancelledEvent e) {
            return route(Topics.ORDER_CANCELLED, e.orderId());
        }
        if (event instanceof PaymentResultEvent e) {
            return route(e.status() == PaymentResultEvent.Status.SUCCESS
                    ? Topics.PAYMENT_COMPLETED : Topics.PAYMENT_FAILED, e.orderId());
        }
        if (event instanceof ReviewPublishedEvent e) {
            return route(Topics.REVIEW_PUBLISHED, e.productId());
        }
        if (event instanceof SupportTicketCreatedEvent e) {
            return route(Topics.SUPPORT_TICKET_CREATED, e.ticketNumber());
        }
        if (event instanceof SupportTicketRepliedEvent e) {
            return route(Topics.SUPPORT_TICKET_REPLIED, e.ticketNumber());
        }
        if (event instanceof CampaignChangedEvent e) {
            return route(e.changeType() == CampaignChangedEvent.ChangeType.ACTIVATED
                    ? Topics.CAMPAIGN_ACTIVATED : Topics.CAMPAIGN_EXPIRED, e.campaignId());
        }
        if (event instanceof LowStockEvent e) {
            return route(Topics.INVENTORY_LOW_STOCK, e.productId());
        }
        return Optional.empty();
    }

    private static Optional<Route> route(String topic, Object key) {
        return Optional.of(new Route(topic, key == null ? null : String.valueOf(key)));
    }
}
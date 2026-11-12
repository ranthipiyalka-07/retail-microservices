package com.retail.order.service;

import com.retail.common.event.OrderCancelledEvent;
import com.retail.common.event.OrderCreatedEvent;
import com.retail.common.event.OrderStatusChangedEvent;
import com.retail.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs only AFTER the transaction commits. Kafka replaces the bridge calls later. */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final NotificationBridge notifications;

    public OrderEventListener(NotificationBridge notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(OrderCreatedEvent event) {
        log.info("{} event (Kafka not enabled yet, handed to notification-service): eventId={}, orderId={}",
                Topics.ORDER_CREATED, event.eventId(), event.orderId());
        notifications.send("ORDER_CREATED", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        log.info("{} event (Kafka not enabled yet, handed to notification-service): eventId={}, orderId={}, {} -> {}",
                Topics.ORDER_STATUS_CHANGED, event.eventId(), event.orderId(), event.oldStatus(), event.newStatus());
        notifications.send("ORDER_STATUS_CHANGED", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(OrderCancelledEvent event) {
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, orderId={}",
                Topics.ORDER_CANCELLED, event.eventId(), event.orderId());
    }
}
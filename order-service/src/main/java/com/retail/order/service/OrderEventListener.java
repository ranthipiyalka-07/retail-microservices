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

/**
 * Runs only AFTER the transaction commits. When Kafka is added, replace each log line with
 * kafkaTemplate.send(topic, orderId, event).
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(OrderCreatedEvent event) {
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, orderId={}",
                Topics.ORDER_CREATED, event.eventId(), event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, orderId={}, {} -> {}",
                Topics.ORDER_STATUS_CHANGED, event.eventId(), event.orderId(), event.oldStatus(), event.newStatus());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(OrderCancelledEvent event) {
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, orderId={}",
                Topics.ORDER_CANCELLED, event.eventId(), event.orderId());
    }
}
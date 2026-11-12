package com.retail.support.service;

import com.retail.common.event.SupportTicketCreatedEvent;
import com.retail.common.event.SupportTicketRepliedEvent;
import com.retail.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs only AFTER the transaction commits. Kafka replaces the bridge calls later. */
@Component
public class SupportEventListener {

    private static final Logger log = LoggerFactory.getLogger(SupportEventListener.class);

    private final NotificationBridge notifications;

    public SupportEventListener(NotificationBridge notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketCreated(SupportTicketCreatedEvent event) {
        log.info("{} event (Kafka not enabled yet, handed to notification-service): eventId={}, ticketNumber={}",
                Topics.SUPPORT_TICKET_CREATED, event.eventId(), event.ticketNumber());
        notifications.send("SUPPORT_TICKET_CREATED", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketReplied(SupportTicketRepliedEvent event) {
        log.info("{} event (Kafka not enabled yet, handed to notification-service): eventId={}, ticketNumber={}",
                Topics.SUPPORT_TICKET_REPLIED, event.eventId(), event.ticketNumber());
        notifications.send("SUPPORT_TICKET_REPLIED", event);
    }
}
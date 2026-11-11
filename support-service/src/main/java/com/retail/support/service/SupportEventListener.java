package com.retail.support.service;

import com.retail.common.event.SupportTicketCreatedEvent;
import com.retail.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only AFTER the transaction commits. When Kafka is added, replace the log line with
 * kafkaTemplate.send(Topics.SUPPORT_TICKET_CREATED, ...): the notification-service sends the acknowledgment email.
 */
@Component
public class SupportEventListener {

    private static final Logger log = LoggerFactory.getLogger(SupportEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketCreated(SupportTicketCreatedEvent event) {
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, ticketNumber={}, customerId={}",
                Topics.SUPPORT_TICKET_CREATED, event.eventId(), event.ticketNumber(), event.customerId());
    }
}
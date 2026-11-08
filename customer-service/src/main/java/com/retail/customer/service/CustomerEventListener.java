package com.retail.customer.service;

import com.retail.common.event.CustomerRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only AFTER the registration transaction commits, so a rolled-back registration never emits an event.
 * When Kafka is added, replace the log line with kafkaTemplate.send(Topics.CUSTOMER_REGISTERED, ...).
 */
@Component
public class CustomerEventListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        log.info("CustomerRegistered event (not sent: Kafka not enabled yet): eventId={}, customerId={}",
                event.eventId(), event.customerId());
    }
}
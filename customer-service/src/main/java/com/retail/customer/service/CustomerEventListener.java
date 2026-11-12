package com.retail.customer.service;

import com.retail.common.event.CustomerRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs only AFTER the registration transaction commits, so a rolled-back registration never sends an email. */
@Component
public class CustomerEventListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventListener.class);

    private final NotificationBridge notifications;

    public CustomerEventListener(NotificationBridge notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        log.info("CustomerRegistered event (Kafka not enabled yet, handed to notification-service): eventId={}, customerId={}",
                event.eventId(), event.customerId());
        notifications.send("CUSTOMER_REGISTERED", event);
    }
}
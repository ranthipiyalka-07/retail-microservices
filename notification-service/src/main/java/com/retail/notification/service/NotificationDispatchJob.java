package com.retail.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Safe on several instances: the claim is an atomic update, so each email is sent by one instance. */
@Component
public class NotificationDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchJob.class);

    private final NotificationDispatcher dispatcher;

    public NotificationDispatchJob(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "${retail.notification.dispatch-delay:PT5S}")
    public void run() {
        try {
            int sent = dispatcher.dispatchDue(50);
            if (sent > 0) {
                log.debug("Dispatch job sent {} emails", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Dispatch job failed: {}", e.toString());
        }
    }
}
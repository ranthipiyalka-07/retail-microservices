package com.retail.order.service;

import com.retail.order.client.NotificationClient;
import com.retail.order.client.NotificationEnvelope;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * TEMPORARY, until Kafka is added: hands an event to notification-service in a background thread.
 * Best effort on purpose: if notification-service is down, or the JVM stops before the call, the email is lost.
 * Kafka with an outbox closes that gap.
 */
@Component
public class NotificationBridge {

    private static final Logger log = LoggerFactory.getLogger(NotificationBridge.class);

    private final NotificationClient client;
    private final String internalKey;
    private final ThreadPoolExecutor executor;

    public NotificationBridge(NotificationClient client, @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.internalKey = internalKey;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(500),
                runnable -> {
                    Thread thread = new Thread(runnable, "notification-bridge");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> log.warn("The notification queue is full: an email was dropped"));
    }

    public void send(String type, Object event) {
        executor.execute(() -> {
            try {
                client.send(internalKey, new NotificationEnvelope(type, event));
            } catch (RuntimeException e) {
                log.warn("Could not hand the {} event to notification-service, so the email is not sent: {}", type, e.toString());
            }
        });
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
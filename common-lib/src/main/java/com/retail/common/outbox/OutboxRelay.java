package com.retail.common.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_BACKOFF_SECONDS = 300;

    private final OutboxStore store;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties props;

    public OutboxRelay(OutboxStore store, KafkaTemplate<String, String> kafka, OutboxProperties props) {
        this.store = store;
        this.kafka = kafka;
        this.props = props;
    }

    @Scheduled(initialDelayString = "${retail.outbox.initial-delay-ms:5000}",
            fixedDelayString = "${retail.outbox.poll-delay-ms:1000}")
    public void run() {
        try {
            relayBatch();
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed: {}", e.toString());
        }
    }

    /** Returns how many events were sent. */
    public int relayBatch() {
        int sent = 0;
        for (OutboxRow row : store.findPending(props.batchSize())) {
            // Not due yet, or another instance is sending it: everything behind it waits, so the order is kept.
            if (!row.due() || !store.claim(row.id(), props.leaseSeconds())) {
                break;
            }
            try {
                send(row);
                store.markSent(row.id());
                sent++;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                int delay = backoffSeconds(row.attempts() + 1);
                store.markFailed(row.id(), delay, describe(e));
                log.warn("Could not send outbox event id={} to {}: {}. Next try in {}s.",
                        row.id(), row.topic(), describe(e), delay);
                break;
            }
        }
        return sent;
    }

    static int backoffSeconds(int attemptsSoFar) {
        long seconds = 1L << Math.min(Math.max(attemptsSoFar, 1), 9);       // 2, 4, 8, 16 ...
        return (int) Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private void send(OutboxRow row) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
        record.headers().add(new RecordHeader("eventId", row.eventId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("eventType", row.eventType().getBytes(StandardCharsets.UTF_8)));
        if (row.traceParent() != null) {
            record.headers().add(new RecordHeader("traceparent", row.traceParent().getBytes(StandardCharsets.UTF_8)));
        }
        kafka.send(record).get(props.sendTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private static String describe(Exception e) {
        Throwable root = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        String text = root.getClass().getSimpleName() + ": " + root.getMessage();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    @Scheduled(cron = "${retail.outbox.cleanup-cron:0 40 3 * * *}")
    public void cleanup() {
        int removed = store.deleteSentOlderThan(props.retentionDays());
        log.info("Outbox cleanup: removed {} sent events older than {} days", removed, props.retentionDays());
    }
}
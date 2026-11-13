package com.retail.common.outbox;

/** due = the retry time has come and nobody else holds the row. traceParent may be null. */
public record OutboxRow(long id, String eventId, String topic, String key, String eventType, String payload,
                        String traceParent, int attempts, boolean due) {}
package com.retail.common.outbox;

import java.util.List;

public interface OutboxStore {

    /** Runs inside the caller's transaction. traceParent is the W3C trace context of the request, or null. */
    void insert(String eventId, String topic, String key, String eventType, String payload, String traceParent);

    /** The oldest unsent rows, in id order. */
    List<OutboxRow> findPending(int limit);

    /** True for the one caller that got the lease. */
    boolean claim(long id, int leaseSeconds);

    void markSent(long id);

    void markFailed(long id, int retryInSeconds, String error);

    int deleteSentOlderThan(int days);
}
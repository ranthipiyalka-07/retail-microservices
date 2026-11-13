package com.retail.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.common.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.function.Supplier;

public class OutboxRecorder {

    private static final Logger log = LoggerFactory.getLogger(OutboxRecorder.class);

    private final OutboxStore store;
    private final ObjectMapper mapper;
    private final Supplier<String> traceParent;

    public OutboxRecorder(OutboxStore store, ObjectMapper mapper, Supplier<String> traceParent) {
        this.store = store;
        this.mapper = mapper;
        this.traceParent = traceParent;
    }

    /** Receives every DomainEvent the service publishes inside a transaction. Runs in the request thread, so the trace is known. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        Optional<EventRouting.Route> route = EventRouting.route(event);
        if (route.isEmpty()) {
            log.debug("No Kafka topic for {}: not stored in the outbox", event.getClass().getSimpleName());
            return;
        }
        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + event.getClass().getSimpleName(), e);
        }
        store.insert(event.eventId().toString(), route.get().topic(), route.get().key(),
                event.getClass().getSimpleName(), payload, traceParent.get());
    }
}
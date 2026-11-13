package com.retail.common.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.common.event.DomainEvent;
import com.retail.common.event.LowStockEvent;
import com.retail.common.event.Topics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OutboxRecorderTest {

    private static final String TRACE = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final OutboxStore store = mock(OutboxStore.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void storesTheEventAsJsonWithItsTopicKeyTypeAndTheCurrentTrace() throws Exception {
        OutboxRecorder recorder = new OutboxRecorder(store, mapper, () -> TRACE);
        UUID id = UUID.randomUUID();

        recorder.record(new LowStockEvent(id, Instant.parse("2026-11-12T10:00:00Z"), 2L, 3, 5));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(store).insert(eq(id.toString()), eq(Topics.INVENTORY_LOW_STOCK), eq("2"), eq("LowStockEvent"),
                payload.capture(), eq(TRACE));
        JsonNode json = new ObjectMapper().readTree(payload.getValue());
        assertThat(json.get("eventId").asText()).isEqualTo(id.toString());
        assertThat(json.get("productId").asLong()).isEqualTo(2L);
    }

    @Test
    void anEventWithoutAnActiveTraceIsStoredWithoutOne() {
        OutboxRecorder recorder = new OutboxRecorder(store, mapper, () -> null);

        recorder.record(new LowStockEvent(UUID.randomUUID(), Instant.now(), 2L, 3, 5));

        verify(store).insert(any(), any(), any(), any(), any(), eq((String) null));
    }

    @Test
    void anEventWithoutATopicIsNotStored() {
        OutboxRecorder recorder = new OutboxRecorder(store, mapper, () -> TRACE);

        recorder.record(new DomainEvent() {
            @Override public UUID eventId() { return UUID.randomUUID(); }
            @Override public Instant occurredAt() { return Instant.now(); }
        });

        verify(store, never()).insert(any(), any(), any(), any(), any(), any());
    }
}
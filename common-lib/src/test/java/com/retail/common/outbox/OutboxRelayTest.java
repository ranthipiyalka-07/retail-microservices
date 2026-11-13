package com.retail.common.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class OutboxRelayTest {

    private final OutboxStore store = mock(OutboxStore.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(store, kafka, new OutboxProperties(true, 50, 60, 10, 7));

    private static OutboxRow row(long id, boolean due, int attempts) {
        return new OutboxRow(id, "evt-" + id, "order.created", "7", "OrderCreatedEvent", "{\"x\":1}",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", attempts, due);
    }
    private void kafkaAccepts() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void sendsDueRowsInOrderAndMarksThemSent() {
        when(store.findPending(50)).thenReturn(List.of(row(1, true, 0), row(2, true, 0)));
        when(store.claim(anyLong(), anyInt())).thenReturn(true);
        kafkaAccepts();

        assertThat(relay.relayBatch()).isEqualTo(2);

        ArgumentCaptor<ProducerRecord> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).topic()).isEqualTo("order.created");
        assertThat(sent.getAllValues().get(0).key()).isEqualTo("7");
        assertThat(sent.getAllValues().get(0).value()).isEqualTo("{\"x\":1}");
        assertThat(new String(sent.getAllValues().get(0).headers().lastHeader("eventId").value(), StandardCharsets.UTF_8))
                .isEqualTo("evt-1");
        var order = inOrder(store);
        order.verify(store).markSent(1L);
        order.verify(store).markSent(2L);
    }

    @Test
    void aFailureSchedulesARetryAndStopsTheBatchSoTheOrderIsKept() {
        when(store.findPending(50)).thenReturn(List.of(row(1, true, 0), row(2, true, 0)));
        when(store.claim(anyLong(), anyInt())).thenReturn(true);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(relay.relayBatch()).isZero();

        verify(store).markFailed(eq(1L), eq(2), contains("broker down"));
        verify(store, never()).claim(eq(2L), anyInt());
        verify(store, never()).markSent(anyLong());
    }

    @Test
    void aRowThatIsNotDueOrNotClaimedStopsTheBatch() {
        when(store.findPending(50)).thenReturn(List.of(row(1, false, 3), row(2, true, 0)));

        assertThat(relay.relayBatch()).isZero();
        verify(store, never()).claim(anyLong(), anyInt());

        when(store.findPending(50)).thenReturn(List.of(row(1, true, 0), row(2, true, 0)));
        when(store.claim(1L, 60)).thenReturn(false);                       // another instance has it
        assertThat(relay.relayBatch()).isZero();
        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    void theBackoffGrowsAndIsCapped() {
        assertThat(OutboxRelay.backoffSeconds(1)).isEqualTo(2);
        assertThat(OutboxRelay.backoffSeconds(2)).isEqualTo(4);
        assertThat(OutboxRelay.backoffSeconds(5)).isEqualTo(32);
        assertThat(OutboxRelay.backoffSeconds(40)).isEqualTo(300);
    }

    @Test
    void theStoredTraceContextTravelsAsAKafkaHeader() {
        when(store.findPending(50)).thenReturn(List.of(row(1, true, 0)));
        when(store.claim(anyLong(), anyInt())).thenReturn(true);
        kafkaAccepts();

        relay.relayBatch();

        ArgumentCaptor<ProducerRecord> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertThat(new String(sent.getValue().headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }
}
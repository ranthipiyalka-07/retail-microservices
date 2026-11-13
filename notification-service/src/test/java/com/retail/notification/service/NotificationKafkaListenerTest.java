package com.retail.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.common.event.Topics;
import com.retail.notification.dto.IntakeResult;
import com.retail.notification.exception.InvalidRequestException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationKafkaListenerTest {

    private final NotificationIntake intake = mock(NotificationIntake.class);
    private final NotificationKafkaListener listener = new NotificationKafkaListener(intake, new ObjectMapper());

    private static ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 42L, "7", value);
    }

    @Test
    void everyTopicIsHandedToTheIntakeWithItsEventType() {
        when(intake.acceptJson(any(), any())).thenReturn(new IntakeResult(1, 0, 0));

        listener.onEvent(record(Topics.ORDER_CREATED, "{\"eventId\":\"e-1\"}"));
        listener.onEvent(record(Topics.INVENTORY_LOW_STOCK, "{\"eventId\":\"e-2\"}"));

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(intake).acceptJson(eq("ORDER_CREATED"), payload.capture());
        assertThat(payload.getValue().get("eventId").asText()).isEqualTo("e-1");
        verify(intake).acceptJson(eq("LOW_STOCK"), any());
    }

    @Test
    void aMessageThatIsNotJsonIsRefusedSoItGoesStraightToTheDeadLetterTopic() {
        assertThatThrownBy(() -> listener.onEvent(record(Topics.ORDER_CREATED, "not json {")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> listener.onEvent(record(Topics.ORDER_CREATED, null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(intake, never()).acceptJson(any(), any());
    }

    @Test
    void aMessageFromAnUnexpectedTopicIsIgnored() {
        listener.onEvent(record("some.other.topic", "{}"));

        verify(intake, never()).acceptJson(any(), any());
    }

    @Test
    void everyTopicTheServiceReadsHasAnEventType() {
        assertThat(NotificationKafkaListener.TOPIC_TYPES).hasSize(6);
    }
}
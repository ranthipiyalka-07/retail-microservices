package com.retail.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.common.event.Topics;
import com.retail.notification.domain.EventType;
import com.retail.notification.dto.IntakeResult;
import com.retail.notification.exception.InvalidRequestException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaListener.class);

    static final Map<String, EventType> TOPIC_TYPES = Map.of(
            Topics.CUSTOMER_REGISTERED, EventType.CUSTOMER_REGISTERED,
            Topics.ORDER_CREATED, EventType.ORDER_CREATED,
            Topics.ORDER_STATUS_CHANGED, EventType.ORDER_STATUS_CHANGED,
            Topics.SUPPORT_TICKET_CREATED, EventType.SUPPORT_TICKET_CREATED,
            Topics.SUPPORT_TICKET_REPLIED, EventType.SUPPORT_TICKET_REPLIED,
            Topics.INVENTORY_LOW_STOCK, EventType.LOW_STOCK);

    private final NotificationIntake intake;
    private final ObjectMapper mapper;

    public NotificationKafkaListener(NotificationIntake intake, ObjectMapper mapper) {
        this.intake = intake;
        this.mapper = mapper;
    }

    @KafkaListener(id = "notification-events", groupId = "notification-service",
            topics = {Topics.CUSTOMER_REGISTERED, Topics.ORDER_CREATED, Topics.ORDER_STATUS_CHANGED,
                    Topics.SUPPORT_TICKET_CREATED, Topics.SUPPORT_TICKET_REPLIED, Topics.INVENTORY_LOW_STOCK})
    public void onEvent(ConsumerRecord<String, String> record) {
        EventType type = TOPIC_TYPES.get(record.topic());
        if (type == null) {
            log.warn("Ignoring a message from the unexpected topic {}", record.topic());
            return;
        }
        JsonNode payload;
        try {
            payload = mapper.readTree(record.value());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidRequestException("The message on " + record.topic() + " is not valid JSON");
        }
        IntakeResult result = intake.acceptJson(type.name(), payload);
        log.info("Kafka event handled: topic={}, partition={}, offset={}, created={}, duplicates={}, skipped={}",
                record.topic(), record.partition(), record.offset(),
                result.created(), result.duplicates(), result.skipped());
    }
}
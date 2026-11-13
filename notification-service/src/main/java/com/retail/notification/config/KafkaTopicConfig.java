package com.retail.notification.config;

import com.retail.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

/** Declares what this service reads, plus a dead-letter topic for each. 3 partitions allow three consumers per topic. */
@Configuration
public class KafkaTopicConfig {

    public static final List<String> SOURCE_TOPICS = List.of(
            Topics.CUSTOMER_REGISTERED, Topics.ORDER_CREATED, Topics.ORDER_STATUS_CHANGED,
            Topics.SUPPORT_TICKET_CREATED, Topics.SUPPORT_TICKET_REPLIED, Topics.INVENTORY_LOW_STOCK);

    @Bean
    public KafkaAdmin.NewTopics notificationTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String name : SOURCE_TOPICS) {
            topics.add(TopicBuilder.name(name).partitions(3).replicas(1).build());
            topics.add(TopicBuilder.name(Topics.dlt(name)).partitions(3).replicas(1).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
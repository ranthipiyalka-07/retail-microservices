package com.retail.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.micrometer.tracing.Tracer;

@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"})
@ConditionalOnClass({KafkaTemplate.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "retail.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxStore outboxStore(JdbcTemplate jdbc) {
        return new JdbcOutboxStore(jdbc);
    }

    @Bean
    public OutboxRecorder outboxRecorder(OutboxStore store, ObjectMapper mapper, ObjectProvider<Tracer> tracers) {
        return new OutboxRecorder(store, mapper, () -> TraceParents.current(tracers.getIfAvailable()));
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxStore store, KafkaTemplate<String, String> kafka, OutboxProperties props) {
        return new OutboxRelay(store, kafka, props);
    }
}
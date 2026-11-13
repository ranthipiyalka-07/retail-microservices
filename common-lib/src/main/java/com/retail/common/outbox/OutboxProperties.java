package com.retail.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Switched on per service with retail.outbox.enabled=true: only services that publish events have the table. */
@ConfigurationProperties(prefix = "retail.outbox")
public record OutboxProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("50") int batchSize,
        @DefaultValue("60") int leaseSeconds,
        @DefaultValue("10") int sendTimeoutSeconds,
        @DefaultValue("7") int retentionDays) {}
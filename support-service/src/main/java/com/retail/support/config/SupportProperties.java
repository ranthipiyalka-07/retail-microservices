package com.retail.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "retail.support")
public record SupportProperties(
        @DefaultValue("10") int maxOpenTickets,                 // per customer (a soft limit)
        @DefaultValue("200") int maxMessagesPerTicket,
        @DefaultValue("7d") Duration autoCloseAfter) {}         // a RESOLVED ticket closes itself after this
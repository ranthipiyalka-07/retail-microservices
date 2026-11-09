package com.retail.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "retail.payment")
public record PaymentProperties(
        @DefaultValue("USD") String currency,                 // assumption A3: one currency in v1
        @DefaultValue("1000000.00") BigDecimal maxAmount,
        @DefaultValue("2m") Duration staleAfter,              // an unfinished payment is checked with the gateway after this
        @DefaultValue("15m") Duration giveUpAfter) {}         // ...and closed as failed if the gateway never saw it
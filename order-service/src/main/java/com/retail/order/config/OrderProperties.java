package com.retail.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "retail.order")
public record OrderProperties(
        @DefaultValue("USD") String currency,                  // must match payment-service (assumption A3)
        @DefaultValue("0") BigDecimal taxRate,                 // a fraction: 0.10 = 10%
        @DefaultValue("0") BigDecimal shippingFee,             // flat fee
        BigDecimal freeShippingThreshold,                      // empty = shipping is never free
        @DefaultValue("2m") Duration staleAfter,               // the reconciler picks up unfinished orders after this
        @DefaultValue("10m") Duration giveUpAfter,             // a PENDING order with no payment is failed after this
        @DefaultValue("30") int maxCompensationAttempts) {}
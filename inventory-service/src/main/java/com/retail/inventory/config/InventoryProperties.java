package com.retail.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "retail.inventory")
public record InventoryProperties(
        @DefaultValue("15m") Duration reservationTtl,
        @DefaultValue("5") int defaultReorderLevel) {}
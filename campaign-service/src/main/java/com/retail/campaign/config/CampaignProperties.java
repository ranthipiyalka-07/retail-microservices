package com.retail.campaign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "retail.campaign")
public record CampaignProperties(
        @DefaultValue("90") BigDecimal maxPercentage,          // the biggest percentage discount allowed
        @DefaultValue("365d") Duration maxDuration,            // the longest a campaign may run
        @DefaultValue("200") int maxTargets) {}                // products and categories per campaign
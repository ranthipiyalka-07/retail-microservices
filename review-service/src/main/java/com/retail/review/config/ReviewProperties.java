package com.retail.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** eligibleStatuses: order statuses that count as "the customer has the product". */
@ConfigurationProperties(prefix = "retail.review")
public record ReviewProperties(@DefaultValue("DELIVERED") List<String> eligibleStatuses) {}
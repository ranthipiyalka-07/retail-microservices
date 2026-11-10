package com.retail.order.dto;

import java.time.Instant;

public record ShipmentResponse(String carrier, String trackingNumber, String trackingUrl,
                               Instant shippedAt, Instant estimatedDelivery, Instant deliveredAt) {}
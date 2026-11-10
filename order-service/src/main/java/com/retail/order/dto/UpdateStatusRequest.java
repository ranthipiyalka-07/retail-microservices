package com.retail.order.dto;

import com.retail.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateStatusRequest(
        @NotNull OrderStatus status,
        @Size(max = 200) String comment,
        @Size(max = 100) String carrier,                       // required for SHIPPED
        @Size(max = 100) String trackingNumber,                // required for SHIPPED
        @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String trackingUrl,
        Instant estimatedDelivery) {}
package com.retail.inventory.dto;

import com.retail.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(Long orderId, List<Line> items) {
    public record Line(Long productId, int quantity, ReservationStatus status, Instant expiresAt) {}
}
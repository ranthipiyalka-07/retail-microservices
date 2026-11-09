package com.retail.inventory.dto;

import java.time.Instant;

public record AuditResponse(Long id, Long productId, int oldQuantity, int newQuantity,
                            String reason, String changedBy, Instant changedAt) {}
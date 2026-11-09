package com.retail.inventory.dto;

import java.time.Instant;

public record StockResponse(Long productId, int quantityOnHand, int quantityReserved, int quantityAvailable,
                            int reorderLevel, boolean lowStock, Instant updatedAt) {}
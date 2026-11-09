package com.retail.inventory.dto;

public record AvailabilityResponse(Long productId, int available, boolean inStock) {}
package com.retail.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetStockRequest(
        @NotNull @Min(0) @Max(1_000_000) Integer quantity,
        @Min(0) @Max(1_000_000) Integer reorderLevel,          // optional: keeps the current level when empty
        @NotBlank @Size(max = 200) String reason) {}           // required: it goes into the audit trail
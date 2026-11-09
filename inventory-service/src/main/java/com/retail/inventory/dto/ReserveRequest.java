package com.retail.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveRequest(
        @NotNull @Positive Long orderId,
        @NotEmpty @Size(max = 50) List<@Valid ReserveItem> items) {}
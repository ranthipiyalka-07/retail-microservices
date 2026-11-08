package com.retail.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String value) {}
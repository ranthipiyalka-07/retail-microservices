package com.retail.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        Long parentId,
        @Size(max = 120) String slug,                 // optional: generated from the name when empty
        Boolean active) {}
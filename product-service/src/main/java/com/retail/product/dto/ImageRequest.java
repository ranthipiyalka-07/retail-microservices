package com.retail.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ImageRequest(
        @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String url,
        @Min(0) Integer sortOrder,
        Boolean primary) {}
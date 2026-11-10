package com.retail.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 150) String title,
        @Size(max = 5000) String comment,
        @Size(max = 50) String displayName,        // shown to other customers; empty = "Verified Buyer"
        @Positive Long orderId) {}                 // optional: review a specific purchase
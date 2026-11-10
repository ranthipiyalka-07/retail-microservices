package com.retail.review.dto;

import com.retail.review.domain.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ModerationRequest(@NotNull ReviewStatus status) {}      // PUBLISHED or HIDDEN
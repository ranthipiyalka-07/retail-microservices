package com.retail.review.dto;

import com.retail.review.domain.ReviewStatus;

import java.time.Instant;

public record MyReviewResponse(Long id, Long productId, Long orderId, int rating, String title, String comment,
                               String displayName, ReviewStatus status, Instant createdAt, Instant updatedAt) {}
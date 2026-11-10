package com.retail.review.dto;

import java.time.Instant;

/** Public view: no customer id, no order id, no email. Every review here is a verified purchase. */
public record ReviewResponse(Long id, int rating, String title, String comment, String displayName,
                             Instant createdAt, Instant updatedAt) {}
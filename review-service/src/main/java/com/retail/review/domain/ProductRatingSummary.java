package com.retail.review.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Changed only through ProductRatingSummaryRepository's atomic statements, never by saving this entity. */
@Entity
@Table(name = "product_rating_summary")
@Getter @Setter
public class ProductRatingSummary {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum;

    @Column(name = "change_version", nullable = false)
    private long changeVersion;

    @Column(name = "synced_version", nullable = false)
    private long syncedVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
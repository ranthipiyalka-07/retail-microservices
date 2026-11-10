package com.retail.review.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.ReviewPublishedEvent;
import com.retail.review.client.PurchaseLookup;
import com.retail.review.config.ReviewProperties;
import com.retail.review.domain.Review;
import com.retail.review.domain.ReviewStatus;
import com.retail.review.dto.*;
import com.retail.review.exception.ConflictException;
import com.retail.review.exception.InvalidRequestException;
import com.retail.review.exception.PurchaseRequiredException;
import com.retail.review.repository.RatingCount;
import com.retail.review.repository.ReviewRepository;
import com.retail.review.repository.ProductRatingSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final String DEFAULT_NAME = "Verified Buyer";

    private final ReviewRepository reviews;
    private final ProductRatingSummaryRepository summaries;
    private final PurchaseLookup purchases;
    private final ApplicationEventPublisher events;
    private final ReviewProperties props;
    private final TransactionTemplate tx;

    public ReviewService(ReviewRepository reviews, ProductRatingSummaryRepository summaries, PurchaseLookup purchases,
                         ApplicationEventPublisher events, ReviewProperties props, PlatformTransactionManager txManager) {
        this.reviews = reviews;
        this.summaries = summaries;
        this.purchases = purchases;
        this.events = events;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    // ================= create =================

    public MyReviewResponse create(Long customerId, Long productId, CreateReviewRequest request) {
        // The remote call first, outside any transaction.
        List<Long> eligible = purchases.find(customerId, productId).stream()
                .filter(p -> props.eligibleStatuses().contains(p.status()))
                .map(PurchaseLookup.Purchase::orderId).sorted().toList();
        if (eligible.isEmpty()) {
            throw new PurchaseRequiredException();
        }
        try {
            return tx.execute(status -> doCreate(customerId, productId, request, eligible));
        } catch (DataIntegrityViolationException e) {
            // Two parallel requests for the same purchase: the unique key lets only one through.
            throw new ConflictException("You have already reviewed this purchase");
        }
    }

    private MyReviewResponse doCreate(Long customerId, Long productId, CreateReviewRequest request, List<Long> eligible) {
        Set<Long> reviewed = new HashSet<>(reviews.findReviewedOrderIds(customerId, productId, ReviewStatus.DELETED));

        Long orderId;
        if (request.orderId() != null) {
            if (!eligible.contains(request.orderId())) {
                throw new PurchaseRequiredException();
            }
            if (reviewed.contains(request.orderId())) {
                throw new ConflictException("You have already reviewed this purchase");
            }
            orderId = request.orderId();
        } else {
            orderId = eligible.stream().filter(id -> !reviewed.contains(id)).findFirst()
                    .orElseThrow(() -> new ConflictException("You have already reviewed every purchase of this product"));
        }

        Review review = new Review();
        review.setProductId(productId);
        review.setCustomerId(customerId);
        review.setOrderId(orderId);
        review.setRating(request.rating());
        review.setTitle(clean(request.title()));
        review.setComment(clean(request.comment()));
        review.setDisplayName(displayName(request.displayName()));
        review.setStatus(ReviewStatus.PUBLISHED);
        reviews.saveAndFlush(review);

        summaries.applyDelta(productId, 1, review.getRating(), Instant.now());
        events.publishEvent(new ReviewPublishedEvent(UUID.randomUUID(), Instant.now(), review.getId(), productId,
                customerId, review.getRating()));
        events.publishEvent(new RatingChanged(productId));
        log.info("Review created: reviewId={}, productId={}, orderId={}", review.getId(), productId, orderId);
        return toMine(review);
    }

    // ================= update / delete / moderate =================

    public MyReviewResponse update(Long customerId, Long productId, Long reviewId, UpdateReviewRequest request) {
        return tx.execute(status -> {
            Review review = reviews.findOwnedForUpdate(reviewId, productId, customerId)
                    .filter(r -> r.getStatus() != ReviewStatus.DELETED)
                    .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
            Instant now = Instant.now();
            int oldRating = review.getRating();

            review.setRating(request.rating());
            review.setTitle(clean(request.title()));
            review.setComment(clean(request.comment()));
            review.setDisplayName(displayName(request.displayName()));
            review.setUpdatedAt(now);

            // Hidden reviews are not counted, so only a published review moves the summary.
            if (review.getStatus() == ReviewStatus.PUBLISHED && oldRating != review.getRating()) {
                summaries.applyDelta(productId, 0, review.getRating() - oldRating, now);
                events.publishEvent(new RatingChanged(productId));
            }
            return toMine(review);
        });
    }

    /** Soft delete. Repeating it is harmless. */
    public void delete(Long customerId, Long productId, Long reviewId) {
        tx.executeWithoutResult(status -> {
            Review review = reviews.findOwnedForUpdate(reviewId, productId, customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
            if (review.getStatus() == ReviewStatus.DELETED) {
                return;
            }
            boolean counted = review.getStatus() == ReviewStatus.PUBLISHED;
            Instant now = Instant.now();
            review.setStatus(ReviewStatus.DELETED);
            review.setUpdatedAt(now);
            if (counted) {
                summaries.applyDelta(productId, -1, -review.getRating(), now);
                events.publishEvent(new RatingChanged(productId));
            }
            log.info("Review deleted: reviewId={}, productId={}", reviewId, productId);
        });
    }

    public MyReviewResponse moderate(Long productId, Long reviewId, ReviewStatus target, String actor) {
        if (target != ReviewStatus.PUBLISHED && target != ReviewStatus.HIDDEN) {
            throw new InvalidRequestException("status must be PUBLISHED or HIDDEN");
        }
        return tx.execute(status -> {
            Review review = reviews.findForUpdate(reviewId, productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
            if (review.getStatus() == ReviewStatus.DELETED) {
                throw new ConflictException("A deleted review cannot be moderated");
            }
            if (review.getStatus() != target) {
                Instant now = Instant.now();
                int sign = target == ReviewStatus.PUBLISHED ? 1 : -1;
                review.setStatus(target);
                review.setUpdatedAt(now);
                summaries.applyDelta(productId, sign, (long) sign * review.getRating(), now);
                events.publishEvent(new RatingChanged(productId));
                log.info("Review moderated: reviewId={}, productId={}, status={}, by={}", reviewId, productId, target, actor);
            }
            return toMine(review);
        });
    }

    // ================= reads =================

    public PageResponse<ReviewResponse> list(Long productId, Integer rating, String sort, Integer page, Integer size) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new InvalidRequestException("rating must be between 1 and 5");
        }
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 50), sortFor(sort));
        Page<Review> result = rating == null
                ? reviews.findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED, pageable)
                : reviews.findByProductIdAndStatusAndRating(productId, ReviewStatus.PUBLISHED, rating, pageable);
        return PageResponse.of(result.map(ReviewService::toPublic));
    }

    public List<MyReviewResponse> mine(Long customerId, Long productId) {
        return reviews.findByCustomerIdAndProductIdAndStatusNotOrderByIdDesc(customerId, productId, ReviewStatus.DELETED)
                .stream().map(ReviewService::toMine).toList();
    }

    /** Computed from the published reviews themselves, so the public numbers are always exact. */
    public RatingSummaryResponse summary(Long productId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0L);
        }
        long total = 0;
        long sum = 0;
        for (RatingCount row : reviews.countByRating(productId, ReviewStatus.PUBLISHED)) {
            distribution.put(row.rating(), row.count());
            total += row.count();
            sum += (long) row.rating() * row.count();
        }
        BigDecimal average = total == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new RatingSummaryResponse(productId, average, (int) total, distribution);
    }

    // ================= helpers =================

    /** Whitelist: the client can never sort by an arbitrary column. */
    private static Sort sortFor(String key) {
        String k = key == null || key.isBlank() ? "newest" : key.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "newest" -> Sort.by(Sort.Order.desc("id"));
            case "oldest" -> Sort.by(Sort.Order.asc("id"));
            case "rating_desc" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("id"));
            case "rating_asc" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("id"));
            default -> throw new InvalidRequestException(
                    "Unknown sort '" + key + "'. Allowed: newest, oldest, rating_desc, rating_asc");
        };
    }

    /** Trims, drops control characters (keeps line breaks and tabs), and turns blank into null. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String displayName(String value) {
        String cleaned = clean(value);
        return cleaned == null ? DEFAULT_NAME : cleaned.replaceAll("\\s+", " ");
    }

    private static ReviewResponse toPublic(Review r) {
        return new ReviewResponse(r.getId(), r.getRating(), r.getTitle(), r.getComment(), r.getDisplayName(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private static MyReviewResponse toMine(Review r) {
        return new MyReviewResponse(r.getId(), r.getProductId(), r.getOrderId(), r.getRating(), r.getTitle(),
                r.getComment(), r.getDisplayName(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
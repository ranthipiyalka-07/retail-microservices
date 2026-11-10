package com.retail.review.service;

import com.retail.common.event.ReviewPublishedEvent;
import com.retail.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Both handlers run only AFTER the transaction commits, so a rolled-back change never triggers anything.
 * When Kafka is added, replace the log line with kafkaTemplate.send(Topics.REVIEW_PUBLISHED, ...).
 */
@Component
public class ReviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final RatingSyncService sync;

    public ReviewEventListener(RatingSyncService sync) {
        this.sync = sync;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewPublished(ReviewPublishedEvent event) {
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, reviewId={}, productId={}",
                Topics.REVIEW_PUBLISHED, event.eventId(), event.reviewId(), event.productId());
    }

    /** Best effort and quick. Whatever fails here is picked up by the sync job. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRatingChanged(RatingChanged event) {
        try {
            sync.sync(event.productId());
        } catch (RuntimeException e) {
            log.warn("Immediate rating sync failed for productId={}: {}", event.productId(), e.toString());
        }
    }
}
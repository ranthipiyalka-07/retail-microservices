package com.retail.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** The ReviewPublished event itself now goes to Kafka through the outbox. This only pushes the rating to product-service. */
@Component
public class ReviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final RatingSyncService sync;

    public ReviewEventListener(RatingSyncService sync) {
        this.sync = sync;
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
package com.retail.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Safe on several instances: product-service ignores a push whose version is not newer. */
@Component
public class RatingSyncJob {

    private static final Logger log = LoggerFactory.getLogger(RatingSyncJob.class);

    private final RatingSyncService sync;

    public RatingSyncJob(RatingSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${retail.review.sync-delay:PT30S}")
    public void run() {
        try {
            int pushed = sync.syncPending(100);
            if (pushed > 0) {
                log.info("Rating sync job pushed {} summaries", pushed);
            }
        } catch (RuntimeException e) {
            log.warn("Rating sync job failed: {}", e.toString());
        }
    }
}
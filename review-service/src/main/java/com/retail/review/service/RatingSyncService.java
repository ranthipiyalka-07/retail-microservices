package com.retail.review.service;

import com.retail.review.client.ProductRatings;
import com.retail.review.domain.ProductRatingSummary;
import com.retail.review.exception.ProductSyncException;
import com.retail.review.repository.ProductRatingSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RatingSyncService {

    private static final Logger log = LoggerFactory.getLogger(RatingSyncService.class);

    private final ProductRatingSummaryRepository summaries;
    private final ProductRatings productRatings;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public RatingSyncService(ProductRatingSummaryRepository summaries, ProductRatings productRatings,
                             PlatformTransactionManager txManager) {
        this.summaries = summaries;
        this.productRatings = productRatings;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Pushes one product's summary if it is newer than what product-service has acknowledged. */
    public void sync(Long productId) {
        ProductRatingSummary summary = readTx.execute(status -> summaries.findById(productId).orElse(null));
        if (summary != null) {
            push(summary);
        }
    }

    /** The safety net: retries everything that is still unsynced. Returns how many were pushed. */
    public int syncPending(int limit) {
        List<ProductRatingSummary> pending = readTx.execute(status -> summaries.findUnsynced(PageRequest.of(0, limit)));
        int pushed = 0;
        if (pending != null) {
            for (ProductRatingSummary summary : pending) {
                if (push(summary)) {
                    pushed++;
                }
            }
        }
        return pushed;
    }

    private boolean push(ProductRatingSummary summary) {
        if (summary.getChangeVersion() <= summary.getSyncedVersion()) {
            return false;
        }
        BigDecimal average = summary.getReviewCount() == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(summary.getRatingSum())
                .divide(BigDecimal.valueOf(summary.getReviewCount()), 2, RoundingMode.HALF_UP);
        try {
            productRatings.push(summary.getProductId(), average, summary.getReviewCount(), summary.getChangeVersion());
        } catch (ProductSyncException e) {
            log.warn("Rating sync failed for productId={} (version {}): {}. It will be retried.",
                    summary.getProductId(), summary.getChangeVersion(), e.getCause() == null ? e : e.getCause().toString());
            return false;
        }
        writeTx.executeWithoutResult(status ->
                summaries.markSynced(summary.getProductId(), summary.getChangeVersion()));
        log.info("Rating synced: productId={}, average={}, count={}, version={}",
                summary.getProductId(), average, summary.getReviewCount(), summary.getChangeVersion());
        return true;
    }
}
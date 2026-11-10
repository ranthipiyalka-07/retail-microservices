package com.retail.review.service;

import com.retail.review.client.ProductRatings;
import com.retail.review.domain.ProductRatingSummary;
import com.retail.review.exception.ProductSyncException;
import com.retail.review.repository.ProductRatingSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingSyncServiceTest {

    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @Mock ProductRatingSummaryRepository summaries;
    @Mock ProductRatings productRatings;

    private RatingSyncService service;

    @BeforeEach
    void setUp() {
        service = new RatingSyncService(summaries, productRatings, new NoOpTransactionManager());
    }

    private static ProductRatingSummary summary(int count, long sum, long changeVersion, long syncedVersion) {
        ProductRatingSummary s = new ProductRatingSummary();
        s.setProductId(7L);
        s.setReviewCount(count);
        s.setRatingSum(sum);
        s.setChangeVersion(changeVersion);
        s.setSyncedVersion(syncedVersion);
        return s;
    }

    @Test
    void pushesTheAverageWithTheVersionAndMarksItSynced() {
        when(summaries.findById(7L)).thenReturn(Optional.of(summary(4, 18, 7, 3)));

        service.sync(7L);

        verify(productRatings).push(eq(7L), argThat(avg -> avg.compareTo(new BigDecimal("4.50")) == 0), eq(4), eq(7L));
        verify(summaries).markSynced(7L, 7L);
    }

    @Test
    void aFailedPushLeavesTheVersionUnsyncedForTheNextRun() {
        when(summaries.findById(7L)).thenReturn(Optional.of(summary(4, 18, 7, 3)));
        doThrow(new ProductSyncException(new RuntimeException("down")))
                .when(productRatings).push(anyLong(), any(BigDecimal.class), anyInt(), anyLong());

        service.sync(7L);

        verify(summaries, never()).markSynced(anyLong(), anyLong());
    }

    @Test
    void anAlreadySyncedSummaryIsNotPushedAgain() {
        when(summaries.findById(7L)).thenReturn(Optional.of(summary(4, 18, 7, 7)));

        service.sync(7L);

        verify(productRatings, never()).push(anyLong(), any(BigDecimal.class), anyInt(), anyLong());
    }

    @Test
    void aProductWithoutReviewsAnymoreGetsAnAverageOfZero() {
        when(summaries.findById(7L)).thenReturn(Optional.of(summary(0, 0, 9, 8)));

        service.sync(7L);

        verify(productRatings).push(eq(7L), argThat(avg -> avg.compareTo(BigDecimal.ZERO) == 0), eq(0), eq(9L));
    }

    @Test
    void theJobPushesEverythingThatIsStillUnsynced() {
        ProductRatingSummary other = summary(1, 5, 2, 0);
        other.setProductId(8L);
        when(summaries.findUnsynced(any(Pageable.class))).thenReturn(List.of(summary(2, 9, 4, 1), other));

        int pushed = service.syncPending(100);

        assertThat(pushed).isEqualTo(2);
        verify(summaries).markSynced(7L, 4L);
        verify(summaries).markSynced(8L, 2L);
    }
}
package com.retail.review.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.ReviewPublishedEvent;
import com.retail.review.client.PurchaseLookup;
import com.retail.review.config.ReviewProperties;
import com.retail.review.domain.Review;
import com.retail.review.domain.ReviewStatus;
import com.retail.review.dto.CreateReviewRequest;
import com.retail.review.dto.MyReviewResponse;
import com.retail.review.dto.RatingSummaryResponse;
import com.retail.review.dto.UpdateReviewRequest;
import com.retail.review.exception.ConflictException;
import com.retail.review.exception.InvalidRequestException;
import com.retail.review.exception.PurchaseRequiredException;
import com.retail.review.repository.ProductRatingSummaryRepository;
import com.retail.review.repository.RatingCount;
import com.retail.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    /** Transactions do nothing in these tests: only the review logic is checked. */
    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @Mock ReviewRepository reviews;
    @Mock ProductRatingSummaryRepository summaries;
    @Mock PurchaseLookup purchases;
    @Mock ApplicationEventPublisher events;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviews, summaries, purchases, events,
                new ReviewProperties(List.of("DELIVERED")), new NoOpTransactionManager());
        when(reviews.saveAndFlush(any(Review.class))).thenAnswer(inv -> {
            Review saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(reviews.findReviewedOrderIds(anyLong(), anyLong(), any())).thenReturn(List.of());
    }

    // ---------- helpers ----------

    private static CreateReviewRequest request(Long orderId) {
        return new CreateReviewRequest(5, "  Great  ", "Soft fabric.", null, orderId);
    }

    private void bought(PurchaseLookup.Purchase... list) {
        when(purchases.find(5L, 7L)).thenReturn(List.of(list));
    }

    private static Review review(long id, int rating, ReviewStatus status) {
        Review r = new Review();
        r.setId(id);
        r.setProductId(7L);
        r.setCustomerId(5L);
        r.setOrderId(11L);
        r.setRating(rating);
        r.setDisplayName("John D.");
        r.setStatus(status);
        return r;
    }

    // ---------- create ----------

    @Test
    void createsAReviewForTheFirstDeliveredOrderAndCountsItInTheSummary() {
        bought(new PurchaseLookup.Purchase(11L, "DELIVERED"), new PurchaseLookup.Purchase(12L, "SHIPPED"));

        MyReviewResponse response = service.create(5L, 7L, request(null));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.orderId()).isEqualTo(11L);
        assertThat(response.title()).isEqualTo("Great");                       // trimmed
        assertThat(response.displayName()).isEqualTo("Verified Buyer");         // default
        assertThat(response.status()).isEqualTo(ReviewStatus.PUBLISHED);
        verify(summaries).applyDelta(eq(7L), eq(1), eq(5L), any(Instant.class));
        verify(events).publishEvent(any(ReviewPublishedEvent.class));
        verify(events).publishEvent(any(RatingChanged.class));
    }

    @Test
    void usesTheNextPurchaseThatHasNoReviewYet() {
        bought(new PurchaseLookup.Purchase(11L, "DELIVERED"), new PurchaseLookup.Purchase(12L, "DELIVERED"));
        when(reviews.findReviewedOrderIds(eq(5L), eq(7L), any())).thenReturn(List.of(11L));

        MyReviewResponse response = service.create(5L, 7L, request(null));

        assertThat(response.orderId()).isEqualTo(12L);
    }

    @Test
    void refusesWhenNothingWasDeliveredYet() {
        bought(new PurchaseLookup.Purchase(11L, "SHIPPED"));

        assertThatThrownBy(() -> service.create(5L, 7L, request(null))).isInstanceOf(PurchaseRequiredException.class);
        verify(reviews, never()).saveAndFlush(any());
        verify(summaries, never()).applyDelta(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyLong(), any());
    }

    @Test
    void refusesAnOrderThatIsNotAnEligiblePurchase() {
        bought(new PurchaseLookup.Purchase(11L, "DELIVERED"));

        assertThatThrownBy(() -> service.create(5L, 7L, request(99L))).isInstanceOf(PurchaseRequiredException.class);
    }

    @Test
    void refusesAPurchaseThatWasAlreadyReviewed() {
        bought(new PurchaseLookup.Purchase(11L, "DELIVERED"));
        when(reviews.findReviewedOrderIds(eq(5L), eq(7L), any())).thenReturn(List.of(11L));

        assertThatThrownBy(() -> service.create(5L, 7L, request(11L))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(5L, 7L, request(null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("every purchase");
    }

    @Test
    void aRaceOnTheUniqueKeyIsAConflict() {
        bought(new PurchaseLookup.Purchase(11L, "DELIVERED"));
        when(reviews.saveAndFlush(any(Review.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(5L, 7L, request(null))).isInstanceOf(ConflictException.class);
    }

    // ---------- update ----------

    @Test
    void editingAPublishedReviewMovesTheSummaryByTheDifference() {
        when(reviews.findOwnedForUpdate(100L, 7L, 5L)).thenReturn(Optional.of(review(100L, 5, ReviewStatus.PUBLISHED)));

        MyReviewResponse response = service.update(5L, 7L, 100L, new UpdateReviewRequest(3, "Okay", "Shrank.", "John D."));

        assertThat(response.rating()).isEqualTo(3);
        verify(summaries).applyDelta(eq(7L), eq(0), eq(-2L), any(Instant.class));
        verify(events).publishEvent(any(RatingChanged.class));
    }

    @Test
    void editingWithoutChangingTheRatingLeavesTheSummaryAlone() {
        when(reviews.findOwnedForUpdate(100L, 7L, 5L)).thenReturn(Optional.of(review(100L, 5, ReviewStatus.PUBLISHED)));

        service.update(5L, 7L, 100L, new UpdateReviewRequest(5, "New title", null, "John D."));

        verify(summaries, never()).applyDelta(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyLong(), any());
    }

    @Test
    void editingAHiddenReviewDoesNotTouchTheSummary() {
        when(reviews.findOwnedForUpdate(100L, 7L, 5L)).thenReturn(Optional.of(review(100L, 5, ReviewStatus.HIDDEN)));

        MyReviewResponse response = service.update(5L, 7L, 100L, new UpdateReviewRequest(2, null, null, null));

        assertThat(response.rating()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(ReviewStatus.HIDDEN);
        verify(summaries, never()).applyDelta(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyLong(), any());
    }

    @Test
    void editingSomeoneElsesOrADeletedReviewIsNotFound() {
        when(reviews.findOwnedForUpdate(100L, 7L, 5L)).thenReturn(Optional.empty());
        when(reviews.findOwnedForUpdate(101L, 7L, 5L)).thenReturn(Optional.of(review(101L, 4, ReviewStatus.DELETED)));

        assertThatThrownBy(() -> service.update(5L, 7L, 100L, new UpdateReviewRequest(3, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(5L, 7L, 101L, new UpdateReviewRequest(3, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void deletingRemovesTheReviewFromTheSummaryOnlyOnce() {
        Review published = review(100L, 4, ReviewStatus.PUBLISHED);
        when(reviews.findOwnedForUpdate(100L, 7L, 5L)).thenReturn(Optional.of(published));

        service.delete(5L, 7L, 100L);
        service.delete(5L, 7L, 100L);                                          // repeating is harmless

        assertThat(published.getStatus()).isEqualTo(ReviewStatus.DELETED);
        verify(summaries, times(1)).applyDelta(eq(7L), eq(-1), eq(-4L), any(Instant.class));
    }

    @Test
    void deletingAHiddenReviewLeavesTheSummaryAlone() {
        Review hidden = review(100L, 4, ReviewStatus.HIDDEN);
        when(reviews.findOwnedForUpdate(100L, 7L, 5L)).thenReturn(Optional.of(hidden));

        service.delete(5L, 7L, 100L);

        assertThat(hidden.getStatus()).isEqualTo(ReviewStatus.DELETED);
        verify(summaries, never()).applyDelta(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyLong(), any());
    }

    // ---------- moderate ----------

    @Test
    void hidingAndUnhidingMovesTheSummary() {
        Review r = review(100L, 4, ReviewStatus.PUBLISHED);
        when(reviews.findForUpdate(100L, 7L)).thenReturn(Optional.of(r));

        service.moderate(7L, 100L, ReviewStatus.HIDDEN, "admin@retail.local");
        verify(summaries).applyDelta(eq(7L), eq(-1), eq(-4L), any(Instant.class));

        service.moderate(7L, 100L, ReviewStatus.PUBLISHED, "admin@retail.local");
        verify(summaries).applyDelta(eq(7L), eq(1), eq(4L), any(Instant.class));
        assertThat(r.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void moderatingToTheCurrentStatusChangesNothing() {
        when(reviews.findForUpdate(100L, 7L)).thenReturn(Optional.of(review(100L, 4, ReviewStatus.PUBLISHED)));

        service.moderate(7L, 100L, ReviewStatus.PUBLISHED, "admin@retail.local");

        verify(summaries, never()).applyDelta(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyLong(), any());
    }

    @Test
    void aDeletedReviewCannotBeModeratedAndDeletedIsNotATarget() {
        when(reviews.findForUpdate(100L, 7L)).thenReturn(Optional.of(review(100L, 4, ReviewStatus.DELETED)));

        assertThatThrownBy(() -> service.moderate(7L, 100L, ReviewStatus.HIDDEN, "admin@retail.local"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.moderate(7L, 100L, ReviewStatus.DELETED, "admin@retail.local"))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---------- reads ----------

    @Test
    void unknownSortAndOutOfRangeRatingAreRejected() {
        assertThatThrownBy(() -> service.list(7L, null, "colour", null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.list(7L, 9, null, null, null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void summaryComputesTheAverageAndFillsInMissingStars() {
        when(reviews.countByRating(7L, ReviewStatus.PUBLISHED))
                .thenReturn(List.of(new RatingCount(5, 2L), new RatingCount(4, 1L)));

        RatingSummaryResponse summary = service.summary(7L);

        assertThat(summary.reviewCount()).isEqualTo(3);
        assertThat(summary.averageRating()).isEqualByComparingTo("4.67");      // 14 / 3
        assertThat(summary.distribution().get(5)).isEqualTo(2L);
        assertThat(summary.distribution().get(1)).isZero();
    }

    @Test
    void summaryOfAProductWithoutReviewsIsZero() {
        when(reviews.countByRating(7L, ReviewStatus.PUBLISHED)).thenReturn(List.of());

        RatingSummaryResponse summary = service.summary(7L);

        assertThat(summary.reviewCount()).isZero();
        assertThat(summary.averageRating()).isEqualByComparingTo("0.00");
    }
}
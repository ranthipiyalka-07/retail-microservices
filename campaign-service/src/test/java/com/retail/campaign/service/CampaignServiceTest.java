package com.retail.campaign.service;

import com.retail.campaign.config.CampaignProperties;
import com.retail.campaign.domain.*;
import com.retail.campaign.dto.CampaignRequest;
import com.retail.campaign.dto.CampaignResponse;
import com.retail.campaign.dto.TargetInput;
import com.retail.campaign.dto.TargetResponse;
import com.retail.campaign.exception.ConflictException;
import com.retail.campaign.exception.InvalidRequestException;
import com.retail.campaign.repository.CampaignRepository;
import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.CampaignChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignServiceTest {

    @Mock CampaignRepository campaigns;
    @Mock ApplicationEventPublisher events;

    private CampaignService service;

    @BeforeEach
    void setUp() {
        service = new CampaignService(campaigns, new CampaignProperties(new BigDecimal("90"), Duration.ofDays(365), 3), events);
        when(campaigns.saveAndFlush(any(Campaign.class))).thenAnswer(inv -> {
            Campaign saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
    }

    // ---------- helpers ----------

    private static Instant in(Duration d) {
        return Instant.now().plus(d);
    }

    private static TargetInput product(long id) { return new TargetInput(TargetType.PRODUCT, id); }

    private static TargetInput category(long id) { return new TargetInput(TargetType.CATEGORY, id); }

    private static CampaignRequest request(DiscountType type, String value, Instant start, Instant end, TargetInput... targets) {
        return new CampaignRequest("  Tee Sale ", " 20% off ", type, new BigDecimal(value), start, end, List.of(targets));
    }

    private static CampaignRequest validRequest(TargetInput... targets) {
        return request(DiscountType.PERCENTAGE, "20", in(Duration.ofHours(1)), in(Duration.ofHours(5)), targets);
    }

    private static Campaign campaign(long id, CampaignStatus status, Instant start, Instant end, long... productIds) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setName("Tee Sale");
        c.setDiscountType(DiscountType.PERCENTAGE);
        c.setDiscountValue(new BigDecimal("20.00"));
        c.setStartAt(start);
        c.setEndAt(end);
        c.setStatus(status);
        c.setCreatedBy("admin@retail.local");
        for (long productId : productIds) {
            CampaignTarget t = new CampaignTarget();
            t.setCampaign(c);
            t.setTargetType(TargetType.PRODUCT);
            t.setTargetId(productId);
            c.getTargets().add(t);
        }
        return c;
    }

    private void campaignExists(Campaign c) {
        when(campaigns.findForUpdate(c.getId())).thenReturn(Optional.of(c));
    }

    // ---------- create ----------

    @Test
    void createStoresADraftWithTrimmedFieldsAndDuplicateTargetsRemoved() {
        CampaignResponse response = service.create(validRequest(product(1), product(1), category(3)), "admin@retail.local");

        assertThat(response.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(response.name()).isEqualTo("Tee Sale");
        assertThat(response.description()).isEqualTo("20% off");
        assertThat(response.discountValue()).isEqualByComparingTo("20.00");
        assertThat(response.createdBy()).isEqualTo("admin@retail.local");
        assertThat(response.live()).isFalse();
        assertThat(response.targets()).extracting(TargetResponse::type)
                .containsExactly(TargetType.PRODUCT, TargetType.CATEGORY);
    }

    @Test
    void createRejectsBadWindowsAndLimits() {
        Instant start = in(Duration.ofHours(1));

        assertThatThrownBy(() -> service.create(request(DiscountType.PERCENTAGE, "20", start, start.minusSeconds(60)), "a"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("after startAt");
        assertThatThrownBy(() -> service.create(request(DiscountType.PERCENTAGE, "20",
                in(Duration.ofDays(-2)), in(Duration.ofDays(-1))), "a"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.create(request(DiscountType.PERCENTAGE, "95", start, in(Duration.ofHours(5))), "a"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("90");
        assertThatThrownBy(() -> service.create(request(DiscountType.PERCENTAGE, "20", start, in(Duration.ofDays(400))), "a"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("365");
        assertThatThrownBy(() -> service.create(validRequest(product(1), product(2), product(3), product(4)), "a"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("at most 3 targets");
    }

    @Test
    void aFixedAmountMayBeLargerThanTheMaximumPercentage() {
        CampaignResponse response = service.create(
                request(DiscountType.FIXED_AMOUNT, "500", in(Duration.ofHours(1)), in(Duration.ofHours(5))), "a");

        assertThat(response.discountValue()).isEqualByComparingTo("500.00");
    }

    // ---------- update ----------

    @Test
    void aDraftAndANotYetStartedScheduledCampaignCanBeEdited() {
        Campaign draft = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofHours(1)), in(Duration.ofHours(5)));
        campaignExists(draft);
        assertThat(service.update(1L, validRequest(product(1))).name()).isEqualTo("Tee Sale");

        Campaign scheduled = campaign(2L, CampaignStatus.SCHEDULED, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        campaignExists(scheduled);
        assertThat(service.update(2L, validRequest(product(1))).targets()).hasSize(1);
    }

    @Test
    void aRunningOrFinishedCampaignCannotBeEdited() {
        Campaign active = campaign(1L, CampaignStatus.ACTIVE, in(Duration.ofHours(-1)), in(Duration.ofHours(5)), 1);
        Campaign startedButNotYetActivated = campaign(2L, CampaignStatus.SCHEDULED, in(Duration.ofMinutes(-1)), in(Duration.ofHours(5)), 1);
        Campaign cancelled = campaign(3L, CampaignStatus.CANCELLED, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        campaignExists(active);
        campaignExists(startedButNotYetActivated);
        campaignExists(cancelled);

        assertThatThrownBy(() -> service.update(1L, validRequest(product(1)))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(2L, validRequest(product(1)))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(3L, validRequest(product(1)))).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateKeepsTheTargetsThatStayAndAddsOnlyTheNewOnes() {
        Campaign draft = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        CampaignTarget kept = draft.getTargets().get(0);
        campaignExists(draft);

        CampaignResponse response = service.update(1L, validRequest(product(1), product(2)));

        assertThat(draft.getTargets()).hasSize(2);
        assertThat(draft.getTargets().get(0)).isSameAs(kept);                   // not deleted and inserted again
        assertThat(response.targets()).extracting(TargetResponse::id).containsExactly(1L, 2L);

        service.update(1L, validRequest(product(2)));
        assertThat(draft.getTargets()).extracting(CampaignTarget::getTargetId).containsExactly(2L);
    }

    @Test
    void anUnknownCampaignIsNotFound() {
        when(campaigns.findForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, validRequest(product(1)))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.publish(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- publish ----------

    @Test
    void publishMovesADraftWithTargetsToScheduled() {
        Campaign draft = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        campaignExists(draft);

        CampaignResponse response = service.publish(1L);

        assertThat(response.status()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(response.live()).isFalse();                                  // it has not started yet
    }

    @Test
    void aPublishedCampaignThatAlreadyStartedIsLiveAtOnce() {
        Campaign draft = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofMinutes(-1)), in(Duration.ofHours(5)), 1);
        campaignExists(draft);

        assertThat(service.publish(1L).live()).isTrue();
    }

    @Test
    void publishNeedsTargetsAndAFutureEndTime() {
        Campaign noTargets = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofHours(1)), in(Duration.ofHours(5)));
        Campaign ended = campaign(2L, CampaignStatus.DRAFT, in(Duration.ofHours(-5)), in(Duration.ofHours(-1)), 1);
        campaignExists(noTargets);
        campaignExists(ended);

        assertThatThrownBy(() -> service.publish(1L)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> service.publish(2L)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("endAt");
    }

    @Test
    void publishIsIdempotentAndRefusesFinishedCampaigns() {
        Campaign scheduled = campaign(1L, CampaignStatus.SCHEDULED, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        Campaign cancelled = campaign(2L, CampaignStatus.CANCELLED, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        campaignExists(scheduled);
        campaignExists(cancelled);

        assertThat(service.publish(1L).status()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThatThrownBy(() -> service.publish(2L)).isInstanceOf(ConflictException.class);
    }

    // ---------- cancel ----------

    @Test
    void cancellingARunningCampaignAnnouncesThatItsPricesEnd() {
        Campaign active = campaign(1L, CampaignStatus.ACTIVE, in(Duration.ofHours(-1)), in(Duration.ofHours(5)), 7, 8);
        campaignExists(active);

        CampaignResponse response = service.cancel(1L);

        assertThat(response.status()).isEqualTo(CampaignStatus.CANCELLED);
        assertThat(response.live()).isFalse();
        ArgumentCaptor<CampaignChangedEvent> event = ArgumentCaptor.forClass(CampaignChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().changeType()).isEqualTo(CampaignChangedEvent.ChangeType.EXPIRED);
        assertThat(event.getValue().campaignId()).isEqualTo(1L);
        assertThat(event.getValue().productIds()).containsExactly(7L, 8L);
    }

    @Test
    void cancellingADraftOrAFutureCampaignAnnouncesNothing() {
        Campaign draft = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        Campaign future = campaign(2L, CampaignStatus.SCHEDULED, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        campaignExists(draft);
        campaignExists(future);

        service.cancel(1L);
        service.cancel(2L);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancelIsIdempotentAndAnExpiredCampaignCannotBeCancelled() {
        Campaign cancelled = campaign(1L, CampaignStatus.CANCELLED, in(Duration.ofHours(1)), in(Duration.ofHours(5)), 1);
        Campaign expired = campaign(2L, CampaignStatus.EXPIRED, in(Duration.ofHours(-5)), in(Duration.ofHours(-1)), 1);
        campaignExists(cancelled);
        campaignExists(expired);

        assertThat(service.cancel(1L).status()).isEqualTo(CampaignStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(2L)).isInstanceOf(ConflictException.class);
    }

    // ---------- delete ----------

    @Test
    void onlyDraftsCanBeDeleted() {
        Campaign draft = campaign(1L, CampaignStatus.DRAFT, in(Duration.ofHours(1)), in(Duration.ofHours(5)));
        Campaign active = campaign(2L, CampaignStatus.ACTIVE, in(Duration.ofHours(-1)), in(Duration.ofHours(5)), 1);
        campaignExists(draft);
        campaignExists(active);

        service.delete(1L);
        verify(campaigns).delete(draft);

        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(ConflictException.class);
        verify(campaigns, never()).delete(active);
    }

    // ---------- lifecycle ----------

    @Test
    void activateAnnouncesOnlyWhenItsOwnUpdateChangedTheRow() {
        Campaign c = campaign(1L, CampaignStatus.ACTIVE, in(Duration.ofMinutes(-1)), in(Duration.ofHours(5)), 7);
        when(campaigns.activateIfDue(eq(1L), eq(CampaignStatus.SCHEDULED), eq(CampaignStatus.ACTIVE), any(Instant.class)))
                .thenReturn(1);
        when(campaigns.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.activate(1L)).isTrue();
        ArgumentCaptor<CampaignChangedEvent> event = ArgumentCaptor.forClass(CampaignChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().changeType()).isEqualTo(CampaignChangedEvent.ChangeType.ACTIVATED);
        assertThat(event.getValue().productIds()).containsExactly(7L);
    }

    @Test
    void aSecondInstanceThatLostTheRaceAnnouncesNothing() {
        when(campaigns.activateIfDue(eq(1L), any(), any(), any(Instant.class))).thenReturn(0);
        when(campaigns.expireIfEnded(eq(1L), any(), any(), any(Instant.class))).thenReturn(0);

        assertThat(service.activate(1L)).isFalse();
        assertThat(service.expire(1L)).isFalse();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void expireAnnouncesAnExpiredEvent() {
        Campaign c = campaign(1L, CampaignStatus.EXPIRED, in(Duration.ofHours(-5)), in(Duration.ofMinutes(-1)), 7);
        when(campaigns.expireIfEnded(eq(1L), any(), eq(CampaignStatus.EXPIRED), any(Instant.class))).thenReturn(1);
        when(campaigns.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.expire(1L)).isTrue();
        ArgumentCaptor<CampaignChangedEvent> event = ArgumentCaptor.forClass(CampaignChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().changeType()).isEqualTo(CampaignChangedEvent.ChangeType.EXPIRED);
    }
}
package com.retail.campaign.service;

import com.retail.campaign.config.CampaignProperties;
import com.retail.campaign.domain.*;
import com.retail.campaign.dto.*;
import com.retail.campaign.exception.ConflictException;
import com.retail.campaign.exception.InvalidRequestException;
import com.retail.campaign.repository.CampaignRepository;
import com.retail.common.audit.Audit;
import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.CampaignChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    /** Statuses in which a campaign can be live (inside its window). */
    static final List<CampaignStatus> OPEN = List.of(CampaignStatus.SCHEDULED, CampaignStatus.ACTIVE);

    private final CampaignRepository campaigns;
    private final CampaignProperties props;
    private final ApplicationEventPublisher events;

    public CampaignService(CampaignRepository campaigns, CampaignProperties props, ApplicationEventPublisher events) {
        this.campaigns = campaigns;
        this.props = props;
        this.events = events;
    }

    // ================= manager side =================

    @Transactional
    public CampaignResponse create(CampaignRequest request, String actor) {
        validate(request);
        Campaign campaign = new Campaign();
        campaign.setCreatedBy(actor);
        campaign.setStatus(CampaignStatus.DRAFT);
        apply(campaign, request);
        campaigns.saveAndFlush(campaign);
        log.info("Campaign created: id={}, by={}", campaign.getId(), actor);
        Audit.record("campaign.create", Audit.Outcome.SUCCESS, "campaign", campaign.getId(), null);
        return toResponse(campaign, Instant.now());
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(Long id) {
        return toResponse(campaigns.findById(id).orElseThrow(() -> new ResourceNotFoundException("Campaign", id)),
                Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignSummary> list(CampaignStatus status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<Campaign> result = status == null ? campaigns.findAll(pageable) : campaigns.findByStatus(status, pageable);
        Instant now = Instant.now();
        return PageResponse.of(result.map(c -> toSummary(c, now)));
    }

    /** Full replace. Only a draft, or a scheduled campaign that has not started yet, can be edited. */
    @Transactional
    public CampaignResponse update(Long id, CampaignRequest request) {
        Campaign campaign = lock(id);
        Instant now = Instant.now();
        boolean editable = campaign.getStatus() == CampaignStatus.DRAFT
                || (campaign.getStatus() == CampaignStatus.SCHEDULED && campaign.getStartAt().isAfter(now));
        if (!editable) {
            throw new ConflictException("A campaign can only be edited while it is a draft or has not started yet. "
                    + "Cancel it and create a new one instead.");
        }
        validate(request);
        apply(campaign, request);
        log.info("Campaign updated: id={}", id);
        return toResponse(campaign, now);
    }

    /** Idempotent: publishing a campaign that is already scheduled or running changes nothing. */
    @Transactional
    public CampaignResponse publish(Long id) {
        Campaign campaign = lock(id);
        Instant now = Instant.now();
        CampaignStatus status = campaign.getStatus();
        if (status == CampaignStatus.SCHEDULED || status == CampaignStatus.ACTIVE) {
            return toResponse(campaign, now);
        }
        if (status != CampaignStatus.DRAFT) {
            throw new ConflictException("A " + status + " campaign cannot be published");
        }
        if (campaign.getTargets().isEmpty()) {
            throw new InvalidRequestException("Add at least one product or category before publishing");
        }
        if (!campaign.getEndAt().isAfter(now)) {
            throw new InvalidRequestException("endAt is in the past: edit the campaign before publishing it");
        }
        campaign.setStatus(CampaignStatus.SCHEDULED);
        log.info("Campaign published: id={}, startAt={}, endAt={}", id, campaign.getStartAt(), campaign.getEndAt());
        Audit.record("campaign.publish", Audit.Outcome.SUCCESS, "campaign", id, null);
        return toResponse(campaign, now);
    }

    /** Idempotent. A campaign that was live tells the other services that its prices end. */
    @Transactional
    public CampaignResponse cancel(Long id) {
        Campaign campaign = lock(id);
        Instant now = Instant.now();
        CampaignStatus status = campaign.getStatus();
        if (status == CampaignStatus.CANCELLED) {
            return toResponse(campaign, now);
        }
        if (status == CampaignStatus.EXPIRED) {
            throw new ConflictException("An expired campaign cannot be cancelled");
        }
        boolean wasLive = status == CampaignStatus.ACTIVE
                || (status == CampaignStatus.SCHEDULED && !campaign.getStartAt().isAfter(now));
        campaign.setStatus(CampaignStatus.CANCELLED);
        if (wasLive) {
            events.publishEvent(changed(campaign, CampaignChangedEvent.ChangeType.EXPIRED));
        }
        log.info("Campaign cancelled: id={}, wasLive={}", id, wasLive);
        return toResponse(campaign, now);
    }

    /** Only drafts are deleted. Everything that ever ran stays as history: cancel it instead. */
    @Transactional
    public void delete(Long id) {
        Campaign campaign = lock(id);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ConflictException("Only a draft can be deleted. Cancel the campaign instead.");
        }
        campaigns.delete(campaign);
        log.info("Campaign deleted: id={}", id);
        Audit.record("campaign.delete", Audit.Outcome.SUCCESS, "campaign", id, null);
    }

    // ================= public =================

    @Transactional(readOnly = true)
    public List<ActiveCampaignResponse> active() {
        return campaigns.findLive(OPEN, Instant.now(), PageRequest.of(0, 100)).stream()
                .map(c -> new ActiveCampaignResponse(c.getId(), c.getName(), c.getDescription(), c.getDiscountType(),
                        c.getDiscountValue(), c.getStartAt(), c.getEndAt()))
                .toList();
    }

    // ================= lifecycle (called by the job) =================

    /** True only for the caller whose update changed the row, so the event is published once. */
    @Transactional
    public boolean activate(Long id) {
        if (campaigns.activateIfDue(id, CampaignStatus.SCHEDULED, CampaignStatus.ACTIVE, Instant.now()) == 0) {
            return false;
        }
        events.publishEvent(changed(campaigns.findById(id).orElseThrow(), CampaignChangedEvent.ChangeType.ACTIVATED));
        log.info("Campaign activated: id={}", id);
        return true;
    }

    @Transactional
    public boolean expire(Long id) {
        if (campaigns.expireIfEnded(id, OPEN, CampaignStatus.EXPIRED, Instant.now()) == 0) {
            return false;
        }
        events.publishEvent(changed(campaigns.findById(id).orElseThrow(), CampaignChangedEvent.ChangeType.EXPIRED));
        log.info("Campaign expired: id={}", id);
        return true;
    }

    // ================= helpers =================

    private Campaign lock(Long id) {
        return campaigns.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Campaign", id));
    }

    private void validate(CampaignRequest r) {
        if (!r.endAt().isAfter(r.startAt())) {
            throw new InvalidRequestException("endAt must be after startAt");
        }
        if (!r.endAt().isAfter(Instant.now())) {
            throw new InvalidRequestException("endAt must be in the future");
        }
        if (Duration.between(r.startAt(), r.endAt()).compareTo(props.maxDuration()) > 0) {
            throw new InvalidRequestException("A campaign can run for at most " + props.maxDuration().toDays() + " days");
        }
        if (r.discountType() == DiscountType.PERCENTAGE && r.discountValue().compareTo(props.maxPercentage()) > 0) {
            throw new InvalidRequestException("A percentage discount can be at most "
                    + props.maxPercentage().toPlainString() + "%");
        }
        if (distinctTargets(r.targets()).size() > props.maxTargets()) {
            throw new InvalidRequestException("A campaign can have at most " + props.maxTargets() + " targets");
        }
    }

    private void apply(Campaign c, CampaignRequest r) {
        c.setName(r.name().trim());
        c.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        c.setDiscountType(r.discountType());
        c.setDiscountValue(r.discountValue().setScale(2, RoundingMode.HALF_UP));
        c.setStartAt(r.startAt());
        c.setEndAt(r.endAt());
        syncTargets(c, r.targets());
    }

    /**
     * Keeps the targets that stay, removes the ones that are gone, adds only the new ones. Replacing the whole list
     * would insert before it deletes and break the unique key on (campaign, type, id).
     */
    private void syncTargets(Campaign c, List<TargetInput> inputs) {
        Map<String, TargetInput> wanted = distinctTargets(inputs);
        c.getTargets().removeIf(t -> !wanted.containsKey(key(t.getTargetType(), t.getTargetId())));
        Set<String> present = c.getTargets().stream()
                .map(t -> key(t.getTargetType(), t.getTargetId())).collect(Collectors.toSet());
        wanted.forEach((k, input) -> {
            if (!present.contains(k)) {
                CampaignTarget target = new CampaignTarget();
                target.setCampaign(c);
                target.setTargetType(input.type());
                target.setTargetId(input.id());
                c.getTargets().add(target);
            }
        });
    }

    private static Map<String, TargetInput> distinctTargets(List<TargetInput> inputs) {
        Map<String, TargetInput> map = new LinkedHashMap<>();
        if (inputs != null) {
            inputs.forEach(t -> map.putIfAbsent(key(t.type(), t.id()), t));
        }
        return map;
    }

    private static String key(TargetType type, Long id) {
        return type + ":" + id;
    }

    /** Category targets cannot be listed as product ids: consumers of the event treat an empty list as "re-read all". */
    private CampaignChangedEvent changed(Campaign c, CampaignChangedEvent.ChangeType type) {
        List<Long> productIds = c.getTargets().stream()
                .filter(t -> t.getTargetType() == TargetType.PRODUCT)
                .map(CampaignTarget::getTargetId).toList();
        return new CampaignChangedEvent(UUID.randomUUID(), Instant.now(), c.getId(), type, productIds);
    }

    private static boolean isLive(Campaign c, Instant now) {
        return OPEN.contains(c.getStatus()) && !c.getStartAt().isAfter(now) && c.getEndAt().isAfter(now);
    }

    private static CampaignResponse toResponse(Campaign c, Instant now) {
        return new CampaignResponse(c.getId(), c.getName(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getStartAt(), c.getEndAt(), c.getStatus(), isLive(c, now), c.getCreatedBy(),
                c.getCreatedAt(), c.getUpdatedAt(),
                c.getTargets().stream().map(t -> new TargetResponse(t.getTargetType(), t.getTargetId())).toList());
    }

    private static CampaignSummary toSummary(Campaign c, Instant now) {
        return new CampaignSummary(c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getStartAt(), c.getEndAt(), c.getStatus(), isLive(c, now), c.getTargets().size());
    }
}
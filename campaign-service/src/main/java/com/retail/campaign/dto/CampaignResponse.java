package com.retail.campaign.dto;

import com.retail.campaign.domain.CampaignStatus;
import com.retail.campaign.domain.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** live = the discount is applied to prices right now. */
public record CampaignResponse(Long id, String name, String description, DiscountType discountType,
                               BigDecimal discountValue, Instant startAt, Instant endAt, CampaignStatus status,
                               boolean live, String createdBy, Instant createdAt, Instant updatedAt,
                               List<TargetResponse> targets) {}
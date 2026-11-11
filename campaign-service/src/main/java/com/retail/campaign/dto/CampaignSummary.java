package com.retail.campaign.dto;

import com.retail.campaign.domain.CampaignStatus;
import com.retail.campaign.domain.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record CampaignSummary(Long id, String name, DiscountType discountType, BigDecimal discountValue,
                              Instant startAt, Instant endAt, CampaignStatus status, boolean live, int targetCount) {}
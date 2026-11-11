package com.retail.campaign.dto;

import com.retail.campaign.domain.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/** Public view: what a promo banner needs. The targets are not listed. */
public record ActiveCampaignResponse(Long id, String name, String description, DiscountType discountType,
                                     BigDecimal discountValue, Instant startAt, Instant endAt) {}
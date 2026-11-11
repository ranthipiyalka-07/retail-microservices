package com.retail.campaign.repository;

import com.retail.campaign.domain.DiscountType;
import com.retail.campaign.domain.TargetType;

import java.math.BigDecimal;

/** One live campaign that targets one product or category. Read-only projection for the price calculation. */
public record ActiveOffer(Long campaignId, String campaignName, DiscountType discountType,
                          BigDecimal discountValue, TargetType targetType, Long targetId) {}
package com.retail.campaign.dto;

import java.math.BigDecimal;

/** campaignId and campaignName are null when no campaign applies. discountAmount is per unit. */
public record PriceResponse(Long productId, BigDecimal basePrice, BigDecimal effectivePrice,
                            BigDecimal discountAmount, Long campaignId, String campaignName) {}
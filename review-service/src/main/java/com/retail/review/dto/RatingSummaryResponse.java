package com.retail.review.dto;

import java.math.BigDecimal;
import java.util.Map;

public record RatingSummaryResponse(Long productId, BigDecimal averageRating, int reviewCount,
                                    Map<Integer, Long> distribution) {}
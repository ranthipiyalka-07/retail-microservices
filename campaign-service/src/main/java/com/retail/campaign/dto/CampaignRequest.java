package com.retail.campaign.dto;

import com.retail.campaign.domain.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Used for both creating and replacing a campaign. Times are ISO-8601 UTC, for example 2026-11-12T00:00:00Z. */
public record CampaignRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Size(max = 1000) List<@Valid TargetInput> targets) {}
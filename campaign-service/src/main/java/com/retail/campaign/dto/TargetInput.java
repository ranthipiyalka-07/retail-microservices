package com.retail.campaign.dto;

import com.retail.campaign.domain.TargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TargetInput(@NotNull TargetType type, @NotNull @Positive Long id) {}
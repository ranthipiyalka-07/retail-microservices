package com.retail.campaign.dto;

import com.retail.campaign.domain.TargetType;

public record TargetResponse(TargetType type, Long id) {}
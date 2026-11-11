package com.retail.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PriceQuery(@NotEmpty @Size(max = 100) List<@Valid PriceQueryItem> items) {}
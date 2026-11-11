package com.retail.support.dto;

import com.retail.support.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull TicketStatus status) {}
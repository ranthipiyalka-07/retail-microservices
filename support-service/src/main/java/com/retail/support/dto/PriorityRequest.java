package com.retail.support.dto;

import com.retail.support.domain.TicketPriority;
import jakarta.validation.constraints.NotNull;

public record PriorityRequest(@NotNull TicketPriority priority) {}
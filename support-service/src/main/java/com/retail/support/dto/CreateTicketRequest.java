package com.retail.support.dto;

import com.retail.support.domain.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotNull TicketCategory category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(min = 10, max = 5000) String description,
        @Positive Long orderId) {}                                  // optional: the order this is about
package com.retail.support.dto;

import com.retail.support.domain.TicketCategory;
import com.retail.support.domain.TicketStatus;

import java.time.Instant;

public record CustomerTicketSummary(Long id, String ticketNumber, TicketCategory category, String subject,
                                    TicketStatus status, Long orderId, Instant createdAt, Instant updatedAt) {}
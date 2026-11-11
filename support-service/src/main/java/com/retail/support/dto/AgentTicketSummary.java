package com.retail.support.dto;

import com.retail.support.domain.TicketCategory;
import com.retail.support.domain.TicketPriority;
import com.retail.support.domain.TicketStatus;

import java.time.Instant;

public record AgentTicketSummary(Long id, String ticketNumber, Long customerId, String customerEmail,
                                 TicketCategory category, String subject, TicketPriority priority,
                                 TicketStatus status, String assignedTo, Long orderId, boolean orderVerified,
                                 Instant createdAt, Instant updatedAt) {}
package com.retail.support.dto;

import com.retail.support.domain.TicketCategory;
import com.retail.support.domain.TicketStatus;

import java.time.Instant;
import java.util.List;

public record CustomerTicketDetail(Long id, String ticketNumber, TicketCategory category, String subject,
                                   String description, TicketStatus status, Long orderId, Instant createdAt,
                                   Instant updatedAt, Instant resolvedAt, List<CustomerMessage> messages) {}
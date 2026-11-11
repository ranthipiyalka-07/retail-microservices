package com.retail.support.service;

import com.retail.support.domain.SupportTicket;
import com.retail.support.domain.TicketMessage;
import com.retail.support.dto.*;

import java.util.List;

/** Maps entities to responses and cleans text input. */
final class TicketMapper {
    private TicketMapper() {}

    /** Message text: drops control characters (keeps line breaks and tabs) and trims. Never null. */
    static String text(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
    }

    /** One-line text (subjects): every control character becomes a space, runs of spaces collapse. Never null. */
    static String line(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    // ---------- customer view ----------

    static CustomerTicketSummary customerSummary(SupportTicket t) {
        return new CustomerTicketSummary(t.getId(), t.getTicketNumber(), t.getCategory(), t.getSubject(),
                t.getStatus(), t.getOrderId(), t.getCreatedAt(), t.getUpdatedAt());
    }

    static CustomerMessage customerMessage(TicketMessage m) {
        return new CustomerMessage(m.getId(), m.getSenderType(), m.getMessage(), m.getCreatedAt());
    }

    static CustomerTicketDetail customerDetail(SupportTicket t, List<TicketMessage> publicMessages) {
        return new CustomerTicketDetail(t.getId(), t.getTicketNumber(), t.getCategory(), t.getSubject(),
                t.getDescription(), t.getStatus(), t.getOrderId(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getResolvedAt(), publicMessages.stream().map(TicketMapper::customerMessage).toList());
    }

    // ---------- agent view ----------

    static AgentTicketSummary agentSummary(SupportTicket t) {
        return new AgentTicketSummary(t.getId(), t.getTicketNumber(), t.getCustomerId(), t.getCustomerEmail(),
                t.getCategory(), t.getSubject(), t.getPriority(), t.getStatus(), t.getAssignedToEmail(),
                t.getOrderId(), t.isOrderVerified(), t.getCreatedAt(), t.getUpdatedAt());
    }

    static AgentMessage agentMessage(TicketMessage m) {
        return new AgentMessage(m.getId(), m.getSenderType(), m.getAuthorEmail(), m.isInternalNote(),
                m.getMessage(), m.getCreatedAt());
    }

    static AgentTicketDetail agentDetail(SupportTicket t, List<TicketMessage> allMessages) {
        return new AgentTicketDetail(t.getId(), t.getTicketNumber(), t.getCustomerId(), t.getCustomerEmail(),
                t.getCategory(), t.getSubject(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAssignedToEmail(), t.getOrderId(), t.isOrderVerified(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getResolvedAt(), t.getClosedAt(), allMessages.stream().map(TicketMapper::agentMessage).toList());
    }
}
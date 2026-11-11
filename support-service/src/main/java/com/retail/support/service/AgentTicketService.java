package com.retail.support.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.support.config.SupportProperties;
import com.retail.support.domain.*;
import com.retail.support.dto.*;
import com.retail.support.exception.ConflictException;
import com.retail.support.exception.InvalidRequestException;
import com.retail.support.exception.LimitExceededException;
import com.retail.support.repository.SupportTicketRepository;
import com.retail.support.repository.TicketMessageRepository;
import com.retail.support.repository.TicketSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AgentTicketService {

    private static final Logger log = LoggerFactory.getLogger(AgentTicketService.class);

    private static final Map<TicketStatus, Set<TicketStatus>> NEXT = Map.of(
            TicketStatus.OPEN, Set.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.CLOSED),
            TicketStatus.ASSIGNED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.RESOLVED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED),
            TicketStatus.CLOSED, Set.of());

    private final SupportTicketRepository tickets;
    private final TicketMessageRepository messages;
    private final SupportProperties props;

    public AgentTicketService(SupportTicketRepository tickets, TicketMessageRepository messages,
                              SupportProperties props) {
        this.tickets = tickets;
        this.messages = messages;
        this.props = props;
    }

    // ================= read =================

    @Transactional(readOnly = true)
    public PageResponse<AgentTicketSummary> list(TicketStatus status, TicketPriority priority, String assignee,
                                                 String sort, Staff staff, Integer page, Integer size) {
        Long assignedTo = null;
        boolean unassignedOnly = false;
        if (assignee != null && !assignee.isBlank()) {
            switch (assignee.trim().toLowerCase(Locale.ROOT)) {
                case "mine" -> assignedTo = staff.userId();
                case "unassigned" -> unassignedOnly = true;
                default -> throw new InvalidRequestException("assignee must be 'mine' or 'unassigned'");
            }
        }
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 100), sortFor(sort));
        return PageResponse.of(tickets.findAll(
                        TicketSpecifications.forAgents(status, priority, assignedTo, unassignedOnly), pageable)
                .map(TicketMapper::agentSummary));
    }

    @Transactional(readOnly = true)
    public AgentTicketDetail get(Long ticketId) {
        SupportTicket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        return detail(ticket);
    }

    // ================= work on a ticket =================

    /** Take the ticket. Another agent's ticket can be taken over. Repeating it as the same agent changes nothing. */
    @Transactional
    public AgentTicketDetail claim(Long ticketId, Staff staff) {
        SupportTicket ticket = lock(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("A " + ticket.getStatus() + " ticket cannot be claimed");
        }
        boolean alreadyMine = staff.userId().equals(ticket.getAssignedTo());
        if (alreadyMine && ticket.getStatus() != TicketStatus.OPEN) {
            return detail(ticket);
        }
        ticket.setAssignedTo(staff.userId());
        ticket.setAssignedToEmail(staff.email());
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.ASSIGNED);
        }
        ticket.setUpdatedAt(Instant.now());
        log.info("Ticket claimed: ticketId={}, by={}", ticketId, staff.email());
        return detail(ticket);
    }

    /**
     * A public reply is what the customer sees. The first one on an unassigned ticket assigns it to the agent and
     * starts the work. An internal note changes nothing (and does not move the customer-visible update time).
     */
    @Transactional
    public AgentMessage reply(Long ticketId, String text, boolean internal, Staff staff) {
        String message = TicketMapper.text(text);
        if (message.isEmpty()) {
            throw new InvalidRequestException("The message must contain visible characters");
        }
        SupportTicket ticket = lock(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("This ticket is closed");
        }
        if (messages.countByTicketId(ticketId) >= props.maxMessagesPerTicket()) {
            throw new LimitExceededException("This ticket has reached the limit of "
                    + props.maxMessagesPerTicket() + " messages");
        }

        TicketMessage saved = messages.save(new TicketMessage(ticketId, SenderType.AGENT, staff.userId(),
                staff.email(), internal, message));

        if (!internal) {
            assignIfNobody(ticket, staff);
            if (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.ASSIGNED) {
                ticket.setStatus(TicketStatus.IN_PROGRESS);
            }
            ticket.setUpdatedAt(Instant.now());
        }
        return TicketMapper.agentMessage(saved);
    }

    @Transactional
    public AgentTicketDetail changeStatus(Long ticketId, TicketStatus target, Staff staff) {
        SupportTicket ticket = lock(ticketId);
        TicketStatus from = ticket.getStatus();
        if (from == target) {
            return detail(ticket);                                 // nothing to do
        }
        if (!NEXT.getOrDefault(from, Set.of()).contains(target)) {
            throw new ConflictException("A ticket that is " + from + " cannot be changed to " + target);
        }

        Instant now = Instant.now();
        if (target == TicketStatus.ASSIGNED || target == TicketStatus.IN_PROGRESS || target == TicketStatus.RESOLVED) {
            assignIfNobody(ticket, staff);
        }
        ticket.setStatus(target);
        if (target == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
        }
        if (from == TicketStatus.RESOLVED && target != TicketStatus.CLOSED) {
            ticket.setResolvedAt(null);                            // reopened: the auto-close timer starts again later
        }
        if (target == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
        }
        ticket.setUpdatedAt(now);
        log.info("Ticket status changed: ticketId={}, {} -> {}, by={}", ticketId, from, target, staff.email());
        return detail(ticket);
    }

    @Transactional
    public AgentTicketDetail changePriority(Long ticketId, TicketPriority priority) {
        SupportTicket ticket = lock(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("A closed ticket cannot be changed");
        }
        ticket.setPriority(priority);                              // internal data: no customer-visible update
        return detail(ticket);
    }

    // ================= helpers =================

    private SupportTicket lock(Long ticketId) {
        return tickets.findForUpdate(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private AgentTicketDetail detail(SupportTicket ticket) {
        return TicketMapper.agentDetail(ticket, messages.findByTicketIdOrderByIdAsc(ticket.getId()));
    }

    private static void assignIfNobody(SupportTicket ticket, Staff staff) {
        if (ticket.getAssignedTo() == null) {
            ticket.setAssignedTo(staff.userId());
            ticket.setAssignedToEmail(staff.email());
        }
    }

    /** Whitelist: the client can never sort by an arbitrary column. "oldest first" is the natural work queue. */
    private static Sort sortFor(String key) {
        String k = key == null || key.isBlank() ? "oldest" : key.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "oldest" -> Sort.by(Sort.Order.asc("id"));
            case "newest" -> Sort.by(Sort.Order.desc("id"));
            default -> throw new InvalidRequestException("Unknown sort '" + key + "'. Allowed: oldest, newest");
        };
    }
}
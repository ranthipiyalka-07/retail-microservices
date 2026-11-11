package com.retail.support.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.SupportTicketCreatedEvent;
import com.retail.support.config.SupportProperties;
import com.retail.support.domain.*;
import com.retail.support.dto.*;
import com.retail.support.exception.ConflictException;
import com.retail.support.exception.InvalidRequestException;
import com.retail.support.exception.LimitExceededException;
import com.retail.support.repository.SupportTicketRepository;
import com.retail.support.repository.TicketMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private static final List<TicketStatus> OPEN_STATUSES =
            List.of(TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** created = false means "this is the ticket of an earlier request with the same Idempotency-Key". */
    public record Created(CustomerTicketDetail ticket, boolean created) {}

    private final SupportTicketRepository tickets;
    private final TicketMessageRepository messages;
    private final SupportProperties props;
    private final ApplicationEventPublisher events;

    public TicketService(SupportTicketRepository tickets, TicketMessageRepository messages,
                         SupportProperties props, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.messages = messages;
        this.props = props;
        this.events = events;
    }

    // ================= create =================

    /** Throws DataIntegrityViolationException when a parallel request with the same key won: see TicketIntake. */
    @Transactional
    public Created create(Long customerId, String customerEmail, String idempotencyKey,
                          CreateTicketRequest request, boolean orderVerified) {
        String subject = TicketMapper.line(request.subject());
        String description = TicketMapper.text(request.description());
        if (subject.isEmpty()) {
            throw new InvalidRequestException("The subject must contain visible characters");
        }
        if (description.length() < 10) {
            throw new InvalidRequestException("Please describe the problem in at least 10 characters");
        }
        // A soft limit: two requests at the same moment can overshoot it by one.
        if (tickets.countByCustomerIdAndStatusIn(customerId, OPEN_STATUSES) >= props.maxOpenTickets()) {
            throw new LimitExceededException("You already have " + props.maxOpenTickets()
                    + " open tickets. Please wait for a reply or close one before opening another.");
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(newTicketNumber());
        ticket.setCustomerId(customerId);
        ticket.setCustomerEmail(customerEmail);
        ticket.setOrderId(request.orderId());
        ticket.setOrderVerified(request.orderId() != null && orderVerified);
        ticket.setCategory(request.category());
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(defaultPriority(request.category()));
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setIdempotencyKey(idempotencyKey);
        tickets.saveAndFlush(ticket);

        events.publishEvent(new SupportTicketCreatedEvent(UUID.randomUUID(), Instant.now(),
                ticket.getTicketNumber(), customerId, customerEmail, subject));
        log.info("Ticket created: ticketNumber={}, customerId={}, category={}", ticket.getTicketNumber(),
                customerId, ticket.getCategory());
        return new Created(TicketMapper.customerDetail(ticket, List.of()), true);
    }

    /** The answer to a retry. The same key with different content is a conflict. */
    @Transactional(readOnly = true)
    public Optional<Created> findReplay(Long customerId, String idempotencyKey, CreateTicketRequest request) {
        return tickets.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).map(t -> {
            boolean same = t.getCategory() == request.category()
                    && t.getSubject().equals(TicketMapper.line(request.subject()))
                    && Objects.equals(t.getOrderId(), request.orderId());
            if (!same) {
                throw new ConflictException("This Idempotency-Key was already used for a different ticket");
            }
            return new Created(TicketMapper.customerDetail(t, publicMessages(t.getId())), false);
        });
    }

    // ================= read =================

    @Transactional(readOnly = true)
    public PageResponse<CustomerTicketSummary> list(Long customerId, TicketStatus status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "id"));
        Page<SupportTicket> result = status == null
                ? tickets.findByCustomerId(customerId, pageable)
                : tickets.findByCustomerIdAndStatus(customerId, status, pageable);
        return PageResponse.of(result.map(TicketMapper::customerSummary));
    }

    /** Somebody else's ticket id is a 404, the same as an unknown one. */
    @Transactional(readOnly = true)
    public CustomerTicketDetail get(Long customerId, Long ticketId) {
        SupportTicket ticket = tickets.findByIdAndCustomerId(ticketId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        return TicketMapper.customerDetail(ticket, publicMessages(ticketId));
    }

    // ================= reply / close =================

    @Transactional
    public CustomerMessage reply(Long customerId, Long ticketId, String text) {
        String message = TicketMapper.text(text);
        if (message.isEmpty()) {
            throw new InvalidRequestException("The message must contain visible characters");
        }
        SupportTicket ticket = tickets.findOwnedForUpdate(ticketId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("This ticket is closed. Please open a new ticket.");
        }
        if (messages.countByTicketId(ticketId) >= props.maxMessagesPerTicket()) {
            throw new LimitExceededException("This ticket has reached the limit of " + props.maxMessagesPerTicket()
                    + " messages. Please open a new ticket.");
        }

        TicketMessage saved = messages.save(new TicketMessage(ticketId, SenderType.CUSTOMER, customerId,
                ticket.getCustomerEmail(), false, message));

        if (ticket.getStatus() == TicketStatus.RESOLVED) {                       // the customer says it is not solved
            ticket.setStatus(ticket.getAssignedTo() != null ? TicketStatus.IN_PROGRESS : TicketStatus.OPEN);
            ticket.setResolvedAt(null);
        }
        ticket.setUpdatedAt(Instant.now());
        return TicketMapper.customerMessage(saved);
    }

    /** Idempotent: closing a closed ticket changes nothing. */
    @Transactional
    public CustomerTicketDetail close(Long customerId, Long ticketId) {
        SupportTicket ticket = tickets.findOwnedForUpdate(ticketId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            Instant now = Instant.now();
            ticket.setStatus(TicketStatus.CLOSED);
            ticket.setClosedAt(now);
            ticket.setUpdatedAt(now);
            log.info("Ticket closed by the customer: ticketId={}", ticketId);
        }
        return TicketMapper.customerDetail(ticket, publicMessages(ticketId));
    }

    // ================= lifecycle (called by the job) =================

    /** True only for the caller whose update changed the row. */
    @Transactional
    public boolean autoClose(Long ticketId, Instant cutoff) {
        boolean closed = tickets.closeIfResolvedBefore(ticketId, TicketStatus.RESOLVED, TicketStatus.CLOSED,
                cutoff, Instant.now()) == 1;
        if (closed) {
            log.info("Ticket closed automatically: ticketId={}", ticketId);
        }
        return closed;
    }

    // ================= helpers =================

    static TicketPriority defaultPriority(TicketCategory category) {
        return switch (category) {
            case PAYMENT -> TicketPriority.HIGH;
            case ORDER, ACCOUNT -> TicketPriority.MEDIUM;
            case PRODUCT, OTHER -> TicketPriority.LOW;
        };
    }

    private List<TicketMessage> publicMessages(Long ticketId) {
        return messages.findByTicketIdAndInternalNoteFalseOrderByIdAsc(ticketId);
    }

    /** TKT-20261110-7K3QXM2P: easy to read out loud (no 0, O, 1, I). */
    private static String newTicketNumber() {
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return "TKT-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }
}
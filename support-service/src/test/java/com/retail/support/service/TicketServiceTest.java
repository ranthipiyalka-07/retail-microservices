package com.retail.support.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.SupportTicketCreatedEvent;
import com.retail.support.config.SupportProperties;
import com.retail.support.domain.*;
import com.retail.support.dto.CreateTicketRequest;
import com.retail.support.dto.CustomerMessage;
import com.retail.support.dto.CustomerTicketDetail;
import com.retail.support.dto.CustomerTicketSummary;
import com.retail.support.dto.PageResponse;
import com.retail.support.exception.ConflictException;
import com.retail.support.exception.InvalidRequestException;
import com.retail.support.exception.LimitExceededException;
import com.retail.support.repository.SupportTicketRepository;
import com.retail.support.repository.TicketMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceTest {

    private static final Instant OLD = Instant.parse("2026-11-01T10:00:00Z");

    @Mock SupportTicketRepository tickets;
    @Mock TicketMessageRepository messages;
    @Mock ApplicationEventPublisher events;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(tickets, messages, new SupportProperties(2, 3, Duration.ofDays(7)), events);
        when(tickets.saveAndFlush(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket saved = inv.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(saved.getCreatedAt());
            return saved;
        });
        when(messages.save(any(TicketMessage.class))).thenAnswer(returnsFirstArg());
        when(messages.countByTicketId(anyLong())).thenReturn(0L);
    }

    // ---------- helpers ----------

    private static CreateTicketRequest request(TicketCategory category, String subject, String description, Long orderId) {
        return new CreateTicketRequest(category, subject, description, orderId);
    }

    private static SupportTicket ticket(long id, TicketStatus status, Long assignedTo) {
        SupportTicket t = new SupportTicket();
        t.setId(id);
        t.setTicketNumber("TKT-TEST");
        t.setCustomerId(5L);
        t.setCustomerEmail("john@example.com");
        t.setCategory(TicketCategory.ORDER);
        t.setSubject("Late order");
        t.setDescription("My order is late.");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(status);
        t.setAssignedTo(assignedTo);
        t.setCreatedAt(OLD);
        t.setUpdatedAt(OLD);
        return t;
    }

    private void owned(SupportTicket t) {
        when(tickets.findOwnedForUpdate(t.getId(), 5L)).thenReturn(Optional.of(t));
    }

    // ---------- create ----------

    @Test
    void createsAnOpenTicketWithAReadableNumberACategoryPriorityAndAnEvent() {
        when(tickets.countByCustomerIdAndStatusIn(eq(5L), any())).thenReturn(0L);

        TicketService.Created created = service.create(5L, "john@example.com", "key-0001-abc",
                request(TicketCategory.PAYMENT, "  Charged \n twice ", "I was charged twice for my order.", 11L), true);

        assertThat(created.created()).isTrue();
        CustomerTicketDetail detail = created.ticket();
        assertThat(detail.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(detail.ticketNumber()).matches("TKT-\\d{8}-[A-HJ-NP-Z2-9]{8}");
        assertThat(detail.subject()).isEqualTo("Charged twice");
        assertThat(detail.orderId()).isEqualTo(11L);
        assertThat(detail.messages()).isEmpty();

        ArgumentCaptor<SupportTicket> stored = ArgumentCaptor.forClass(SupportTicket.class);
        verify(tickets).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(stored.getValue().isOrderVerified()).isTrue();
        assertThat(stored.getValue().getCustomerEmail()).isEqualTo("john@example.com");
        assertThat(stored.getValue().getIdempotencyKey()).isEqualTo("key-0001-abc");
        verify(events).publishEvent(any(SupportTicketCreatedEvent.class));
    }

    @Test
    void aTicketWithoutAnOrderIsNeverMarkedAsHavingAVerifiedOrder() {
        when(tickets.countByCustomerIdAndStatusIn(eq(5L), any())).thenReturn(0L);

        service.create(5L, "john@example.com", null,
                request(TicketCategory.PRODUCT, "Sizing", "Does the tee run small or large?", null), true);

        ArgumentCaptor<SupportTicket> stored = ArgumentCaptor.forClass(SupportTicket.class);
        verify(tickets).saveAndFlush(stored.capture());
        assertThat(stored.getValue().isOrderVerified()).isFalse();
        assertThat(stored.getValue().getPriority()).isEqualTo(TicketPriority.LOW);
    }

    @Test
    void thePriorityFollowsTheCategory() {
        assertThat(TicketService.defaultPriority(TicketCategory.PAYMENT)).isEqualTo(TicketPriority.HIGH);
        assertThat(TicketService.defaultPriority(TicketCategory.ORDER)).isEqualTo(TicketPriority.MEDIUM);
        assertThat(TicketService.defaultPriority(TicketCategory.ACCOUNT)).isEqualTo(TicketPriority.MEDIUM);
        assertThat(TicketService.defaultPriority(TicketCategory.PRODUCT)).isEqualTo(TicketPriority.LOW);
        assertThat(TicketService.defaultPriority(TicketCategory.OTHER)).isEqualTo(TicketPriority.LOW);
    }

    @Test
    void tooManyOpenTicketsAreRefused() {
        when(tickets.countByCustomerIdAndStatusIn(eq(5L), any())).thenReturn(2L);       // the limit in these tests

        assertThatThrownBy(() -> service.create(5L, "john@example.com", null,
                request(TicketCategory.ORDER, "Late", "My order is late, please help.", null), false))
                .isInstanceOf(LimitExceededException.class).hasMessageContaining("2 open tickets");
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void anEmptySubjectOrAShortDescriptionAfterCleaningIsRefused() {
        when(tickets.countByCustomerIdAndStatusIn(eq(5L), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(5L, "john@example.com", null,
                request(TicketCategory.ORDER, "\u0001 \u0002", "My order is late, please help.", null), false))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create(5L, "john@example.com", null,
                request(TicketCategory.ORDER, "Late", "short\u0000\u0000", null), false))
                .isInstanceOf(InvalidRequestException.class);
        verify(tickets, never()).saveAndFlush(any());
    }

    // ---------- replay ----------

    @Test
    void aRetryWithTheSameKeyAndContentReturnsTheExistingTicket() {
        SupportTicket existing = ticket(1L, TicketStatus.OPEN, null);
        existing.setSubject("Late order");
        when(tickets.findByCustomerIdAndIdempotencyKey(5L, "key-0001-abc")).thenReturn(Optional.of(existing));

        Optional<TicketService.Created> replay = service.findReplay(5L, "key-0001-abc",
                request(TicketCategory.ORDER, " Late   order ", "My order is late, please help.", null));

        assertThat(replay).isPresent();
        assertThat(replay.get().created()).isFalse();
        assertThat(replay.get().ticket().id()).isEqualTo(1L);
    }

    @Test
    void theSameKeyWithDifferentContentIsAConflictAndAnUnknownKeyIsEmpty() {
        SupportTicket existing = ticket(1L, TicketStatus.OPEN, null);
        when(tickets.findByCustomerIdAndIdempotencyKey(5L, "key-0001-abc")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.findReplay(5L, "key-0001-abc",
                request(TicketCategory.PAYMENT, "Something else", "A completely different problem.", null)))
                .isInstanceOf(ConflictException.class);
        assertThat(service.findReplay(5L, "other-key-0002", request(TicketCategory.ORDER, "Late order",
                "My order is late, please help.", null))).isEmpty();
    }

    // ---------- reply ----------

    @Test
    void aCustomerReplyIsStoredAndMovesTheUpdateTimeButNotTheStatus() {
        SupportTicket t = ticket(1L, TicketStatus.IN_PROGRESS, 9L);
        owned(t);

        CustomerMessage message = service.reply(5L, 1L, "  Any news?  ");

        assertThat(message.sender()).isEqualTo(SenderType.CUSTOMER);
        assertThat(message.message()).isEqualTo("Any news?");
        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(t.getUpdatedAt()).isAfter(OLD);
        ArgumentCaptor<TicketMessage> stored = ArgumentCaptor.forClass(TicketMessage.class);
        verify(messages).save(stored.capture());
        assertThat(stored.getValue().getSenderId()).isEqualTo(5L);
        assertThat(stored.getValue().isInternalNote()).isFalse();
    }

    @Test
    void aReplyToAResolvedTicketReopensItToWhereItWas() {
        SupportTicket assigned = ticket(1L, TicketStatus.RESOLVED, 9L);
        assigned.setResolvedAt(OLD);
        SupportTicket unassigned = ticket(2L, TicketStatus.RESOLVED, null);
        unassigned.setResolvedAt(OLD);
        owned(assigned);
        owned(unassigned);

        service.reply(5L, 1L, "Still broken");
        service.reply(5L, 2L, "Still broken");

        assertThat(assigned.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(assigned.getResolvedAt()).isNull();
        assertThat(unassigned.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(unassigned.getResolvedAt()).isNull();
    }

    @Test
    void aClosedTicketCannotBeRepliedToAndTheMessageLimitHolds() {
        SupportTicket closed = ticket(1L, TicketStatus.CLOSED, 9L);
        SupportTicket full = ticket(2L, TicketStatus.OPEN, null);
        owned(closed);
        owned(full);
        when(messages.countByTicketId(2L)).thenReturn(3L);                           // the limit in these tests

        assertThatThrownBy(() -> service.reply(5L, 1L, "Hello")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reply(5L, 2L, "Hello")).isInstanceOf(LimitExceededException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void anEmptyReplyAndSomeoneElsesTicketAreRefused() {
        assertThatThrownBy(() -> service.reply(5L, 1L, "\u0001  ")).isInstanceOf(InvalidRequestException.class);
        when(tickets.findOwnedForUpdate(77L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(5L, 77L, "Hello")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- close ----------

    @Test
    void closingSetsTheClosedTimeOnceAndRepeatingItChangesNothing() {
        SupportTicket t = ticket(1L, TicketStatus.IN_PROGRESS, 9L);
        owned(t);

        service.close(5L, 1L);
        Instant closedAt = t.getClosedAt();
        service.close(5L, 1L);

        assertThat(t.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(closedAt).isNotNull();
        assertThat(t.getClosedAt()).isEqualTo(closedAt);
    }

    // ---------- read ----------

    @Test
    void anotherCustomersTicketIsNotFound() {
        when(tickets.findByIdAndCustomerId(1L, 6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(6L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theCustomerOnlyGetsThePublicMessages() {
        SupportTicket t = ticket(1L, TicketStatus.IN_PROGRESS, 9L);
        when(tickets.findByIdAndCustomerId(1L, 5L)).thenReturn(Optional.of(t));
        TicketMessage reply = new TicketMessage(1L, SenderType.AGENT, 9L, "agent@retail.local", false, "We are on it.");
        when(messages.findByTicketIdAndInternalNoteFalseOrderByIdAsc(1L)).thenReturn(List.of(reply));

        CustomerTicketDetail detail = service.get(5L, 1L);

        assertThat(detail.messages()).hasSize(1);
        assertThat(detail.messages().get(0).sender()).isEqualTo(SenderType.AGENT);
        verify(messages, never()).findByTicketIdOrderByIdAsc(anyLong());             // internal notes are never read
    }

    @Test
    void listClampsThePageSizeAndFiltersByStatus() {
        when(tickets.findByCustomerIdAndStatus(eq(5L), eq(TicketStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket(1L, TicketStatus.OPEN, null)), PageRequest.of(0, 50), 1));

        PageResponse<CustomerTicketSummary> result = service.list(5L, TicketStatus.OPEN, -2, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tickets).findByCustomerIdAndStatus(eq(5L), eq(TicketStatus.OPEN), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        assertThat(result.content().get(0).ticketNumber()).isEqualTo("TKT-TEST");
    }

    // ---------- lifecycle ----------

    @Test
    void autoCloseReportsWhetherItsOwnUpdateChangedTheRow() {
        when(tickets.closeIfResolvedBefore(eq(1L), eq(TicketStatus.RESOLVED), eq(TicketStatus.CLOSED), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(tickets.closeIfResolvedBefore(eq(2L), eq(TicketStatus.RESOLVED), eq(TicketStatus.CLOSED), any(Instant.class), any(Instant.class)))
                .thenReturn(0);                                                      // reopened by the customer in the meantime

        assertThat(service.autoClose(1L, Instant.now())).isTrue();
        assertThat(service.autoClose(2L, Instant.now())).isFalse();
    }
}
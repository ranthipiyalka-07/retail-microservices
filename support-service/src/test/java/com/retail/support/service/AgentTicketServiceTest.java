package com.retail.support.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.support.config.SupportProperties;
import com.retail.support.domain.*;
import com.retail.support.dto.AgentMessage;
import com.retail.support.dto.AgentTicketDetail;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTicketServiceTest {

    private static final Instant OLD = Instant.parse("2026-11-01T10:00:00Z");

    @Mock SupportTicketRepository tickets;
    @Mock TicketMessageRepository messages;

    private AgentTicketService service;
    private final Staff agent = new Staff(9L, "agent@retail.local");

    @BeforeEach
    void setUp() {
        service = new AgentTicketService(tickets, messages, new SupportProperties(2, 3, Duration.ofDays(7)));
        when(messages.save(any(TicketMessage.class))).thenAnswer(returnsFirstArg());
        when(messages.countByTicketId(anyLong())).thenReturn(0L);
    }

    // ---------- helpers ----------

    private SupportTicket locked(long id, TicketStatus status, Long assignedTo) {
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
        t.setAssignedToEmail(assignedTo == null ? null : "someone@retail.local");
        t.setCreatedAt(OLD);
        t.setUpdatedAt(OLD);
        when(tickets.findForUpdate(id)).thenReturn(Optional.of(t));
        return t;
    }

    // ---------- claim ----------

    @Test
    void claimingAnOpenTicketAssignsItToTheAgent() {
        SupportTicket t = locked(1L, TicketStatus.OPEN, null);

        AgentTicketDetail detail = service.claim(1L, agent);

        assertThat(detail.status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(t.getAssignedTo()).isEqualTo(9L);
        assertThat(t.getAssignedToEmail()).isEqualTo("agent@retail.local");
        assertThat(t.getUpdatedAt()).isAfter(OLD);
    }

    @Test
    void claimingYourOwnTicketAgainChangesNothing() {
        SupportTicket t = locked(1L, TicketStatus.IN_PROGRESS, 9L);

        service.claim(1L, agent);

        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(t.getUpdatedAt()).isEqualTo(OLD);
    }

    @Test
    void anotherAgentCanTakeOverAndTheStatusStays() {
        SupportTicket t = locked(1L, TicketStatus.IN_PROGRESS, 3L);

        service.claim(1L, agent);

        assertThat(t.getAssignedTo()).isEqualTo(9L);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void aResolvedOrClosedTicketCannotBeClaimedAndAnUnknownOneIsNotFound() {
        locked(1L, TicketStatus.RESOLVED, 3L);
        locked(2L, TicketStatus.CLOSED, 3L);
        when(tickets.findForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(1L, agent)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.claim(2L, agent)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.claim(99L, agent)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- reply ----------

    @Test
    void theFirstPublicReplyAssignsTheTicketAndStartsTheWork() {
        SupportTicket t = locked(1L, TicketStatus.OPEN, null);

        AgentMessage message = service.reply(1L, "  We are checking.  ", false, agent);

        assertThat(message.sender()).isEqualTo(SenderType.AGENT);
        assertThat(message.author()).isEqualTo("agent@retail.local");
        assertThat(message.internal()).isFalse();
        assertThat(message.message()).isEqualTo("We are checking.");
        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(t.getAssignedTo()).isEqualTo(9L);
        assertThat(t.getUpdatedAt()).isAfter(OLD);
    }

    @Test
    void aPublicReplyKeepsTheExistingAssignee() {
        SupportTicket t = locked(1L, TicketStatus.ASSIGNED, 3L);

        service.reply(1L, "We are checking.", false, agent);

        assertThat(t.getAssignedTo()).isEqualTo(3L);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void anInternalNoteChangesNeitherTheStatusNorTheAssigneeNorTheUpdateTime() {
        SupportTicket t = locked(1L, TicketStatus.OPEN, null);

        AgentMessage note = service.reply(1L, "Customer has two earlier tickets.", true, agent);

        assertThat(note.internal()).isTrue();
        assertThat(t.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(t.getAssignedTo()).isNull();
        assertThat(t.getUpdatedAt()).isEqualTo(OLD);
        ArgumentCaptor<TicketMessage> stored = ArgumentCaptor.forClass(TicketMessage.class);
        verify(messages).save(stored.capture());
        assertThat(stored.getValue().isInternalNote()).isTrue();
    }

    @Test
    void aPublicReplyOnAResolvedTicketLeavesItResolved() {
        SupportTicket t = locked(1L, TicketStatus.RESOLVED, 9L);

        service.reply(1L, "Anything else we can help with?", false, agent);

        assertThat(t.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void aClosedTicketTheMessageLimitAndEmptyTextAreRefused() {
        locked(1L, TicketStatus.CLOSED, 9L);
        locked(2L, TicketStatus.OPEN, null);
        when(messages.countByTicketId(2L)).thenReturn(3L);                           // the limit in these tests

        assertThatThrownBy(() -> service.reply(1L, "Hello", false, agent)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reply(2L, "Hello", false, agent)).isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> service.reply(2L, "\u0001 ", false, agent)).isInstanceOf(InvalidRequestException.class);
        verify(messages, never()).save(any());
    }

    // ---------- status ----------

    @Test
    void theStatusFollowsTheAllowedStepsAndKeepsTheTimestampsRight() {
        SupportTicket t = locked(1L, TicketStatus.ASSIGNED, 9L);

        service.changeStatus(1L, TicketStatus.IN_PROGRESS, agent);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        service.changeStatus(1L, TicketStatus.RESOLVED, agent);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(t.getResolvedAt()).isNotNull();

        service.changeStatus(1L, TicketStatus.IN_PROGRESS, agent);                   // an agent reopens it
        assertThat(t.getResolvedAt()).isNull();

        service.changeStatus(1L, TicketStatus.CLOSED, agent);
        assertThat(t.getClosedAt()).isNotNull();
        assertThatThrownBy(() -> service.changeStatus(1L, TicketStatus.IN_PROGRESS, agent))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void closingAResolvedTicketKeepsWhenItWasResolved() {
        SupportTicket t = locked(1L, TicketStatus.RESOLVED, 9L);
        t.setResolvedAt(OLD);

        service.changeStatus(1L, TicketStatus.CLOSED, agent);

        assertThat(t.getResolvedAt()).isEqualTo(OLD);
        assertThat(t.getClosedAt()).isNotNull();
    }

    @Test
    void stepsCannotBeSkipped() {
        locked(1L, TicketStatus.OPEN, null);

        assertThatThrownBy(() -> service.changeStatus(1L, TicketStatus.RESOLVED, agent))
                .isInstanceOf(ConflictException.class).hasMessageContaining("cannot be changed to RESOLVED");
    }

    @Test
    void movingToWorkAssignsTheAgentWhenNobodyOwnsTheTicketYet() {
        SupportTicket t = locked(1L, TicketStatus.OPEN, null);

        service.changeStatus(1L, TicketStatus.IN_PROGRESS, agent);

        assertThat(t.getAssignedTo()).isEqualTo(9L);
        assertThat(t.getAssignedToEmail()).isEqualTo("agent@retail.local");
    }

    @Test
    void closingWithoutWorkingOnTheTicketDoesNotAssignIt() {
        SupportTicket t = locked(1L, TicketStatus.OPEN, null);

        service.changeStatus(1L, TicketStatus.CLOSED, agent);                        // for example spam

        assertThat(t.getAssignedTo()).isNull();
    }

    @Test
    void settingTheCurrentStatusAgainIsANoOp() {
        SupportTicket t = locked(1L, TicketStatus.ASSIGNED, 9L);

        service.changeStatus(1L, TicketStatus.ASSIGNED, agent);

        assertThat(t.getUpdatedAt()).isEqualTo(OLD);
    }

    // ---------- priority ----------

    @Test
    void thePriorityCanBeChangedWithoutTouchingTheCustomerVisibleUpdateTime() {
        SupportTicket t = locked(1L, TicketStatus.IN_PROGRESS, 9L);

        service.changePriority(1L, TicketPriority.HIGH);

        assertThat(t.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(t.getUpdatedAt()).isEqualTo(OLD);
    }

    @Test
    void aClosedTicketsPriorityCannotBeChanged() {
        locked(1L, TicketStatus.CLOSED, 9L);

        assertThatThrownBy(() -> service.changePriority(1L, TicketPriority.HIGH)).isInstanceOf(ConflictException.class);
    }

    // ---------- read ----------

    @Test
    void theAgentSeesInternalNotesAndTheCustomersEmail() {
        SupportTicket t = locked(1L, TicketStatus.IN_PROGRESS, 9L);
        when(tickets.findById(1L)).thenReturn(Optional.of(t));
        when(messages.findByTicketIdOrderByIdAsc(1L)).thenReturn(List.of(
                new TicketMessage(1L, SenderType.AGENT, 9L, "agent@retail.local", true, "Internal note")));

        AgentTicketDetail detail = service.get(1L);

        assertThat(detail.customerEmail()).isEqualTo("john@example.com");
        assertThat(detail.messages()).hasSize(1);
        assertThat(detail.messages().get(0).internal()).isTrue();
        assertThat(detail.messages().get(0).author()).isEqualTo("agent@retail.local");
    }

    @Test
    void anUnknownTicketIsNotFound() {
        when(tickets.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theQueueIsOldestFirstByDefaultAndClampsThePageSize() {
        when(tickets.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, agent, -1, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tickets).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void theQueueCanBeFilteredByAssigneeAndSortedNewestFirst() {
        when(tickets.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(TicketStatus.OPEN, TicketPriority.HIGH, "MINE", "newest", agent, 0, 10);
        service.list(null, null, "unassigned", null, agent, 0, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tickets, org.mockito.Mockito.times(2)).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void anUnknownAssigneeOrSortIsRefused() {
        assertThatThrownBy(() -> service.list(null, null, "bob", null, agent, 0, 10))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.list(null, null, null, "colour", agent, 0, 10))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("Allowed");
    }
}
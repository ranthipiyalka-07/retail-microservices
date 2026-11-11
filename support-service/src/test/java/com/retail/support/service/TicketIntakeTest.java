package com.retail.support.service;

import com.retail.support.client.OrderVerifier;
import com.retail.support.domain.TicketCategory;
import com.retail.support.domain.TicketStatus;
import com.retail.support.dto.CreateTicketRequest;
import com.retail.support.dto.CustomerTicketDetail;
import com.retail.support.exception.ConflictException;
import com.retail.support.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketIntakeTest {

    private static final String KEY = "ticket-test-0001";

    @Mock TicketService tickets;
    @Mock OrderVerifier orders;

    private TicketIntake intake;

    @BeforeEach
    void setUp() {
        intake = new TicketIntake(tickets, orders);
    }

    // ---------- helpers ----------

    private static CreateTicketRequest request(Long orderId) {
        return new CreateTicketRequest(TicketCategory.ORDER, "Late order", "My order is late, please help.", orderId);
    }

    private static TicketService.Created created(boolean isNew) {
        CustomerTicketDetail detail = new CustomerTicketDetail(1L, "TKT-TEST", TicketCategory.ORDER, "Late order",
                "My order is late, please help.", TicketStatus.OPEN, null, Instant.now(), Instant.now(), null, List.of());
        return new TicketService.Created(detail, isNew);
    }

    // ---------- order check ----------

    @Test
    void aTicketWithoutAnOrderNeverAsksOrderService() {
        when(tickets.create(eq(5L), eq("john@example.com"), eq(null), any(), eq(false))).thenReturn(created(true));

        TicketService.Created result = intake.create(5L, "john@example.com", null, request(null));

        assertThat(result.created()).isTrue();
        verifyNoInteractions(orders);
    }

    @Test
    void anOrderOfTheCustomerIsMarkedVerified() {
        when(orders.check(5L, 11L)).thenReturn(OrderVerifier.Result.OWNED);
        when(tickets.create(eq(5L), any(), any(), any(), eq(true))).thenReturn(created(true));

        intake.create(5L, "john@example.com", null, request(11L));

        verify(tickets).create(eq(5L), eq("john@example.com"), eq(null), any(), eq(true));
    }

    @Test
    void anOrderOfSomeoneElseOrAnUnknownOrderIsRefusedAndNothingIsCreated() {
        when(orders.check(5L, 11L)).thenReturn(OrderVerifier.Result.NOT_OWNED);

        assertThatThrownBy(() -> intake.create(5L, "john@example.com", null, request(11L)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not found in your orders");
        verify(tickets, never()).create(anyLong(), any(), any(), any(), anyBoolean());
    }

    @Test
    void whenOrderServiceCannotBeAskedTheTicketIsKeptButUnverified() {
        when(orders.check(5L, 11L)).thenReturn(OrderVerifier.Result.UNKNOWN);
        when(tickets.create(eq(5L), any(), any(), any(), eq(false))).thenReturn(created(true));

        TicketService.Created result = intake.create(5L, "john@example.com", null, request(11L));

        assertThat(result.created()).isTrue();
        verify(tickets).create(eq(5L), eq("john@example.com"), eq(null), any(), eq(false));
    }

    // ---------- idempotency ----------

    @Test
    void aRetryIsAnsweredBeforeOrderServiceIsAskedAndNothingIsCreated() {
        when(tickets.findReplay(eq(5L), eq(KEY), any())).thenReturn(Optional.of(created(false)));

        TicketService.Created result = intake.create(5L, "john@example.com", KEY, request(11L));

        assertThat(result.created()).isFalse();
        verifyNoInteractions(orders);
        verify(tickets, never()).create(anyLong(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aParallelRequestWithTheSameKeyIsAnsweredWithTheWinnersTicket() {
        when(tickets.findReplay(eq(5L), eq(KEY), any())).thenReturn(Optional.empty(), Optional.of(created(false)));
        when(orders.check(5L, 11L)).thenReturn(OrderVerifier.Result.OWNED);
        when(tickets.create(eq(5L), any(), eq(KEY), any(), anyBoolean())).thenThrow(new DataIntegrityViolationException("duplicate"));

        TicketService.Created result = intake.create(5L, "john@example.com", KEY, request(11L));

        assertThat(result.created()).isFalse();
    }

    @Test
    void aDuplicateKeyErrorWithoutAFoundTicketIsAConflict() {
        when(tickets.findReplay(eq(5L), eq(KEY), any())).thenReturn(Optional.empty());
        when(tickets.create(eq(5L), any(), eq(KEY), any(), anyBoolean())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> intake.create(5L, "john@example.com", KEY, request(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aDatabaseConflictWithoutAKeyIsAConflict() {
        when(tickets.create(eq(5L), any(), eq(null), any(), anyBoolean())).thenThrow(new DataIntegrityViolationException("odd"));

        assertThatThrownBy(() -> intake.create(5L, "john@example.com", null, request(null)))
                .isInstanceOf(ConflictException.class);
        verify(tickets, never()).findReplay(anyLong(), any(), any());
    }
}
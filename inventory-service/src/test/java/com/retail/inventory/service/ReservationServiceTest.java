package com.retail.inventory.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.inventory.config.InventoryProperties;
import com.retail.inventory.domain.InventoryAudit;
import com.retail.inventory.domain.InventoryItem;
import com.retail.inventory.domain.ReservationStatus;
import com.retail.inventory.domain.StockReservation;
import com.retail.inventory.dto.AvailabilityResponse;
import com.retail.inventory.dto.ReservationResponse;
import com.retail.inventory.dto.ReserveItem;
import com.retail.inventory.dto.ReserveRequest;
import com.retail.inventory.exception.ConflictException;
import com.retail.inventory.exception.InvalidRequestException;
import com.retail.inventory.exception.StockShortageException;
import com.retail.inventory.repository.InventoryAuditRepository;
import com.retail.inventory.repository.InventoryItemRepository;
import com.retail.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    /** Transactions do nothing in these tests: only the stock logic is checked. */
    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @Mock InventoryItemRepository items;
    @Mock StockReservationRepository reservations;
    @Mock InventoryAuditRepository audits;
    @Mock LowStockNotifier lowStock;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(items, reservations, audits, lowStock,
                new InventoryProperties(Duration.ofMinutes(15), 5), new NoOpTransactionManager());
    }

    private static StockReservation reservation(long id, long orderId, long productId, int quantity, ReservationStatus status) {
        StockReservation r = new StockReservation();
        r.setId(id);
        r.setOrderId(orderId);
        r.setProductId(productId);
        r.setQuantity(quantity);
        r.setStatus(status);
        r.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        return r;
    }

    private static InventoryItem item(long productId, int onHand, int reserved, int reorderLevel) {
        InventoryItem i = new InventoryItem();
        i.setProductId(productId);
        i.setQuantityOnHand(onHand);
        i.setQuantityReserved(reserved);
        i.setReorderLevel(reorderLevel);
        return i;
    }

    private void noExistingReservation(long orderId) {
        when(reservations.findByOrderIdOrderByProductIdAsc(orderId)).thenReturn(List.of());
    }

    private void saveEchoesTheRows() {
        when(reservations.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- reserve ----------

    @Test
    void reservesEveryItemAndAnswersWithTheReservationLines() {
        noExistingReservation(7L);
        saveEchoesTheRows();
        when(items.reserve(eq(1L), eq(2), any(Instant.class))).thenReturn(1);
        when(items.reserve(eq(2L), eq(1), any(Instant.class))).thenReturn(1);
        when(items.findByProductId(1L)).thenReturn(Optional.of(item(1L, 20, 2, 5)));    // available 18
        when(items.findByProductId(2L)).thenReturn(Optional.of(item(2L, 20, 1, 5)));    // available 19

        ReservationResponse response = service.reserve(
                new ReserveRequest(7L, List.of(new ReserveItem(1L, 2), new ReserveItem(2L, 1))));

        assertThat(response.orderId()).isEqualTo(7L);
        assertThat(response.items()).extracting(ReservationResponse.Line::productId).containsExactly(1L, 2L);
        assertThat(response.items()).allMatch(l -> l.status() == ReservationStatus.RESERVED);
        assertThat(response.items().get(0).expiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
        verify(lowStock, times(2)).notifyIfCrossed(any(InventoryItem.class), eq(20));   // available before = 18 + 2, 19 + 1
    }

    @Test
    void notEnoughStockListsTheProductAndStopsTheReservation() {
        noExistingReservation(7L);
        saveEchoesTheRows();
        when(items.reserve(eq(1L), eq(5), any(Instant.class))).thenReturn(0);
        when(items.reserve(eq(2L), eq(1), any(Instant.class))).thenReturn(1);
        when(items.findByProductId(1L)).thenReturn(Optional.of(item(1L, 3, 0, 5)));

        assertThatThrownBy(() -> service.reserve(
                new ReserveRequest(7L, List.of(new ReserveItem(1L, 5), new ReserveItem(2L, 1)))))
                .isInstanceOf(StockShortageException.class)
                .hasMessageContaining("product 1")
                .hasMessageContaining("available 3");
        verifyNoInteractions(lowStock);
    }

    @Test
    void sameProductTwiceIsAddedUpAndProductsAreProcessedInAscendingOrder() {
        noExistingReservation(7L);
        saveEchoesTheRows();
        when(items.reserve(anyLong(), anyInt(), any(Instant.class))).thenReturn(1);
        when(items.findByProductId(anyLong())).thenAnswer(inv ->
                Optional.of(item(inv.<Long>getArgument(0), 100, 0, 5)));

        service.reserve(new ReserveRequest(7L, List.of(
                new ReserveItem(2L, 1), new ReserveItem(1L, 1), new ReserveItem(2L, 2))));

        InOrder order = inOrder(items);
        order.verify(items).reserve(eq(1L), eq(1), any(Instant.class));
        order.verify(items).reserve(eq(2L), eq(3), any(Instant.class));
    }

    @Test
    void retryWithTheSameItemsReturnsTheExistingReservationWithoutTouchingStock() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of(
                reservation(1, 7, 1, 2, ReservationStatus.RESERVED),
                reservation(2, 7, 2, 1, ReservationStatus.RESERVED)));

        ReservationResponse response = service.reserve(
                new ReserveRequest(7L, List.of(new ReserveItem(2L, 1), new ReserveItem(1L, 2))));

        assertThat(response.items()).hasSize(2);
        verifyNoInteractions(items, lowStock);
        verify(reservations, never()).saveAllAndFlush(any());
    }

    @Test
    void retryWithDifferentItemsIsAConflict() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of(
                reservation(1, 7, 1, 2, ReservationStatus.RESERVED)));

        assertThatThrownBy(() -> service.reserve(new ReserveRequest(7L, List.of(new ReserveItem(1L, 5)))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different items");
    }

    @Test
    void reservingAgainAfterTheReservationExpiredIsAConflict() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of(
                reservation(1, 7, 1, 2, ReservationStatus.EXPIRED)));

        assertThatThrownBy(() -> service.reserve(new ReserveRequest(7L, List.of(new ReserveItem(1L, 2)))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Start a new order");
    }

    @Test
    void aParallelCallForTheSameOrderIsRepeatedAndAnsweredAsARetry() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L))
                .thenReturn(List.of())
                .thenReturn(List.of(reservation(1, 7, 1, 2, ReservationStatus.RESERVED)));
        when(reservations.saveAllAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        ReservationResponse response = service.reserve(new ReserveRequest(7L, List.of(new ReserveItem(1L, 2))));

        assertThat(response.items()).hasSize(1);
        verifyNoInteractions(items);
    }

    // ---------- commit ----------

    @Test
    void commitSellsTheReservedStockAndWritesAnAuditRow() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L))
                .thenReturn(List.of(reservation(10, 7, 1, 2, ReservationStatus.RESERVED)))
                .thenReturn(List.of(reservation(10, 7, 1, 2, ReservationStatus.COMMITTED)));
        when(reservations.transition(eq(10L), eq(ReservationStatus.RESERVED), eq(ReservationStatus.COMMITTED),
                any(Instant.class))).thenReturn(1);
        when(items.commitSale(eq(1L), eq(2), any(Instant.class))).thenReturn(1);
        when(items.findByProductId(1L)).thenReturn(Optional.of(item(1L, 18, 0, 5)));

        ReservationResponse response = service.commit(7L);

        assertThat(response.items().get(0).status()).isEqualTo(ReservationStatus.COMMITTED);
        ArgumentCaptor<InventoryAudit> audit = ArgumentCaptor.forClass(InventoryAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getOldQuantity()).isEqualTo(20);
        assertThat(audit.getValue().getNewQuantity()).isEqualTo(18);
        assertThat(audit.getValue().getReason()).contains("order=7");
    }

    @Test
    void committingTwiceIsHarmless() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of(
                reservation(10, 7, 1, 2, ReservationStatus.COMMITTED)));

        ReservationResponse response = service.commit(7L);

        assertThat(response.items().get(0).status()).isEqualTo(ReservationStatus.COMMITTED);
        verifyNoInteractions(items, audits);
        verify(reservations, never()).transition(any(), any(), any(), any());
    }

    @Test
    void anExpiredReservationCannotBeCommitted() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of(
                reservation(10, 7, 1, 2, ReservationStatus.EXPIRED)));

        assertThatThrownBy(() -> service.commit(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EXPIRED");
        verifyNoInteractions(items, audits);
    }

    @Test
    void committingAnUnknownOrderGivesNotFound() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.commit(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- release ----------

    @Test
    void releasingAnUnpaidReservationGivesTheHoldBack() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L))
                .thenReturn(List.of(reservation(10, 7, 1, 2, ReservationStatus.RESERVED)))
                .thenReturn(List.of(reservation(10, 7, 1, 2, ReservationStatus.RELEASED)));
        when(reservations.transition(eq(10L), eq(ReservationStatus.RESERVED), eq(ReservationStatus.RELEASED),
                any(Instant.class))).thenReturn(1);
        when(items.unreserve(eq(1L), eq(2), any(Instant.class))).thenReturn(1);

        ReservationResponse response = service.release(7L);

        assertThat(response.items().get(0).status()).isEqualTo(ReservationStatus.RELEASED);
        verify(items).unreserve(eq(1L), eq(2), any(Instant.class));
        verifyNoInteractions(audits);
    }

    @Test
    void cancellingAPaidOrderPutsTheUnitsBackOnTheShelfWithAnAuditRow() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L))
                .thenReturn(List.of(reservation(10, 7, 1, 2, ReservationStatus.COMMITTED)))
                .thenReturn(List.of(reservation(10, 7, 1, 2, ReservationStatus.RELEASED)));
        when(reservations.transition(eq(10L), eq(ReservationStatus.COMMITTED), eq(ReservationStatus.RELEASED),
                any(Instant.class))).thenReturn(1);
        when(items.restock(eq(1L), eq(2), any(Instant.class))).thenReturn(1);
        when(items.findByProductId(1L)).thenReturn(Optional.of(item(1L, 12, 0, 5)));

        service.release(7L);

        ArgumentCaptor<InventoryAudit> audit = ArgumentCaptor.forClass(InventoryAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getOldQuantity()).isEqualTo(10);
        assertThat(audit.getValue().getNewQuantity()).isEqualTo(12);
        assertThat(audit.getValue().getReason()).contains("CANCEL");
    }

    @Test
    void releasingAnUnknownOrderIsNotAnError() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of());

        ReservationResponse response = service.release(7L);

        assertThat(response.items()).isEmpty();
        verifyNoInteractions(items);
    }

    @Test
    void anAlreadyReleasedReservationIsLeftAlone() {
        when(reservations.findByOrderIdOrderByProductIdAsc(7L)).thenReturn(List.of(
                reservation(10, 7, 1, 2, ReservationStatus.RELEASED)));

        service.release(7L);

        verifyNoInteractions(items, audits);
        verify(reservations, never()).transition(any(), any(), any(), any());
    }

    // ---------- expiry ----------

    @Test
    void anExpiredReservationReturnsItsStock() {
        when(reservations.findById(10L)).thenReturn(Optional.of(reservation(10, 7, 1, 2, ReservationStatus.RESERVED)));
        when(reservations.transitionIfExpired(eq(10L), eq(ReservationStatus.RESERVED), eq(ReservationStatus.EXPIRED),
                any(Instant.class))).thenReturn(1);
        when(items.unreserve(eq(1L), eq(2), any(Instant.class))).thenReturn(1);

        assertThat(service.expire(10L)).isTrue();
    }

    @Test
    void aReservationThatWasAlreadyHandledInParallelIsNotExpiredTwice() {
        when(reservations.findById(10L)).thenReturn(Optional.of(reservation(10, 7, 1, 2, ReservationStatus.RESERVED)));
        when(reservations.transitionIfExpired(eq(10L), eq(ReservationStatus.RESERVED), eq(ReservationStatus.EXPIRED),
                any(Instant.class))).thenReturn(0);

        assertThat(service.expire(10L)).isFalse();
        verifyNoInteractions(items);
    }

    // ---------- availability ----------

    @Test
    void availabilityReportsUnknownProductsAsOutOfStock() {
        when(items.findByProductIdIn(any())).thenReturn(List.of(item(1L, 10, 4, 5)));

        List<AvailabilityResponse> result = service.availability(List.of(1L, 99L, 1L));

        assertThat(result).hasSize(2);                                      // the duplicate id is dropped
        assertThat(result.get(0).available()).isEqualTo(6);
        assertThat(result.get(0).inStock()).isTrue();
        assertThat(result.get(1).productId()).isEqualTo(99L);
        assertThat(result.get(1).inStock()).isFalse();
    }

    @Test
    void availabilityRejectsTooManyProducts() {
        List<Long> tooMany = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> service.availability(tooMany)).isInstanceOf(InvalidRequestException.class);
    }
}
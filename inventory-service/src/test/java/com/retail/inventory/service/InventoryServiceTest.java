package com.retail.inventory.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.inventory.config.InventoryProperties;
import com.retail.inventory.domain.InventoryAudit;
import com.retail.inventory.domain.InventoryItem;
import com.retail.inventory.dto.SetStockRequest;
import com.retail.inventory.dto.StockResponse;
import com.retail.inventory.exception.ConflictException;
import com.retail.inventory.repository.InventoryAuditRepository;
import com.retail.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryItemRepository items;
    @Mock InventoryAuditRepository audits;
    @Mock LowStockNotifier lowStock;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(items, audits, lowStock, new InventoryProperties(Duration.ofMinutes(15), 5));
    }

    private static InventoryItem item(long productId, int onHand, int reserved, int reorderLevel) {
        InventoryItem i = new InventoryItem();
        i.setProductId(productId);
        i.setQuantityOnHand(onHand);
        i.setQuantityReserved(reserved);
        i.setReorderLevel(reorderLevel);
        return i;
    }

    @Test
    void firstStockEntryCreatesTheRecordWithTheDefaultReorderLevelAndAnAuditRow() {
        when(items.findForUpdate(5L)).thenReturn(Optional.empty());
        when(items.saveAndFlush(any(InventoryItem.class))).thenAnswer(returnsFirstArg());

        StockResponse response = service.setStock(5L, new SetStockRequest(20, null, " Initial stock "), "admin@retail.local");

        assertThat(response.quantityOnHand()).isEqualTo(20);
        assertThat(response.quantityAvailable()).isEqualTo(20);
        assertThat(response.reorderLevel()).isEqualTo(5);
        ArgumentCaptor<InventoryAudit> audit = ArgumentCaptor.forClass(InventoryAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getOldQuantity()).isZero();
        assertThat(audit.getValue().getNewQuantity()).isEqualTo(20);
        assertThat(audit.getValue().getReason()).isEqualTo("Initial stock");
        assertThat(audit.getValue().getChangedBy()).isEqualTo("admin@retail.local");
        verify(lowStock).notifyIfCrossed(any(InventoryItem.class), eq(Integer.MAX_VALUE));
    }

    @Test
    void updatingStockWritesAnAuditRowAndKeepsTheReorderLevelWhenNotGiven() {
        InventoryItem existing = item(5L, 50, 10, 10);
        when(items.findForUpdate(5L)).thenReturn(Optional.of(existing));
        when(items.saveAndFlush(any(InventoryItem.class))).thenAnswer(returnsFirstArg());

        StockResponse response = service.setStock(5L, new SetStockRequest(30, null, "Stock count"), "manager@retail.local");

        assertThat(response.quantityOnHand()).isEqualTo(30);
        assertThat(response.reorderLevel()).isEqualTo(10);
        ArgumentCaptor<InventoryAudit> audit = ArgumentCaptor.forClass(InventoryAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getOldQuantity()).isEqualTo(50);
        assertThat(audit.getValue().getNewQuantity()).isEqualTo(30);
        verify(lowStock).notifyIfCrossed(existing, 40);               // available was 50 - 10
    }

    @Test
    void settingTheSameQuantityWritesNoAuditRow() {
        when(items.findForUpdate(5L)).thenReturn(Optional.of(item(5L, 50, 0, 10)));
        when(items.saveAndFlush(any(InventoryItem.class))).thenAnswer(returnsFirstArg());

        service.setStock(5L, new SetStockRequest(50, 20, "Raise the reorder level"), "manager@retail.local");

        verify(audits, never()).save(any());
    }

    @Test
    void stockCannotBeSetBelowTheReservedQuantity() {
        when(items.findForUpdate(5L)).thenReturn(Optional.of(item(5L, 20, 8, 5)));

        assertThatThrownBy(() -> service.setStock(5L, new SetStockRequest(5, null, "Recount"), "manager@retail.local"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("8 units are reserved");
        verify(audits, never()).save(any());
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void twoManagersCreatingTheSameRecordAtOnceGiveAConflictForTheLoser() {
        when(items.findForUpdate(5L)).thenReturn(Optional.empty());
        when(items.saveAndFlush(any(InventoryItem.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.setStock(5L, new SetStockRequest(10, null, "Initial"), "manager@retail.local"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void readingAnUnknownProductGivesNotFound() {
        when(items.findByProductId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
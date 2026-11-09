package com.retail.inventory.service;

import com.retail.common.event.LowStockEvent;
import com.retail.inventory.domain.InventoryItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LowStockNotifierTest {

    @Mock ApplicationEventPublisher events;
    @InjectMocks LowStockNotifier notifier;

    private static InventoryItem item(int onHand, int reserved, int reorderLevel) {
        InventoryItem i = new InventoryItem();
        i.setProductId(1L);
        i.setQuantityOnHand(onHand);
        i.setQuantityReserved(reserved);
        i.setReorderLevel(reorderLevel);
        return i;
    }

    @Test
    void publishesWhenStockCrossesTheReorderLevel() {
        notifier.notifyIfCrossed(item(10, 8, 5), 6);                 // 6 -> 2, level 5

        ArgumentCaptor<LowStockEvent> captor = ArgumentCaptor.forClass(LowStockEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo(1L);
        assertThat(captor.getValue().quantityAvailable()).isEqualTo(2);
        assertThat(captor.getValue().reorderLevel()).isEqualTo(5);
    }

    @Test
    void doesNotRepeatTheAlertWhileStockStaysLow() {
        notifier.notifyIfCrossed(item(10, 9, 5), 3);                 // already low before: 3 -> 1

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void doesNotPublishWhileStockIsAboveTheLevel() {
        notifier.notifyIfCrossed(item(50, 2, 5), 50);

        verify(events, never()).publishEvent(any(Object.class));
    }
}
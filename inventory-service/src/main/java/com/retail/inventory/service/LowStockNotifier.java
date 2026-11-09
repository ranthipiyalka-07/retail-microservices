package com.retail.inventory.service;

import com.retail.common.event.LowStockEvent;
import com.retail.inventory.domain.InventoryItem;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class LowStockNotifier {

    private final ApplicationEventPublisher events;

    public LowStockNotifier(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** previousAvailable is the available quantity before the change that was just made. */
    public void notifyIfCrossed(InventoryItem item, int previousAvailable) {
        int available = item.available();
        if (available <= item.getReorderLevel() && previousAvailable > item.getReorderLevel()) {
            events.publishEvent(new LowStockEvent(UUID.randomUUID(), Instant.now(),
                    item.getProductId(), available, item.getReorderLevel()));
        }
    }
}
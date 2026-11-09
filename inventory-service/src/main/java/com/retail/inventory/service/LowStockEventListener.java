package com.retail.inventory.service;

import com.retail.common.event.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only AFTER the transaction commits. When Kafka is added, replace the log line with
 * kafkaTemplate.send(Topics.INVENTORY_LOW_STOCK, ...).
 */
@Component
public class LowStockEventListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        log.warn("LowStock event (not sent: Kafka not enabled yet): productId={}, available={}, reorderLevel={}",
                event.productId(), event.quantityAvailable(), event.reorderLevel());
    }
}
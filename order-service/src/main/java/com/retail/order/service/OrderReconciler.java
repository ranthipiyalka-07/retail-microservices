package com.retail.order.service;

import com.retail.order.config.OrderProperties;
import com.retail.order.domain.CustomerOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Finishes what a timeout or a crash left behind. Safe on several instances: every step is idempotent. */
@Component
public class OrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final OrderStateService state;
    private final OrderSaga saga;
    private final OrderProperties props;

    public OrderReconciler(OrderStateService state, OrderSaga saga, OrderProperties props) {
        this.state = state;
        this.saga = saga;
        this.props = props;
    }

    @Scheduled(initialDelayString = "PT60S", fixedDelayString = "${retail.order.reconcile-delay:PT1M}")
    public void run() {
        Instant cutoff = Instant.now().minus(props.staleAfter());

        for (CustomerOrder order : state.stalePending(cutoff, BATCH_SIZE)) {
            try {
                saga.settle(order.getId(), null);
            } catch (RuntimeException e) {
                log.warn("Could not settle orderId={}: {}", order.getId(), e.toString());
            }
        }
        for (CustomerOrder order : state.needingCompensation(cutoff, props.maxCompensationAttempts(), BATCH_SIZE)) {
            try {
                saga.compensate(order.getId());
            } catch (RuntimeException e) {
                log.warn("Could not finish the follow-up work of orderId={}: {}", order.getId(), e.toString());
            }
        }
    }
}
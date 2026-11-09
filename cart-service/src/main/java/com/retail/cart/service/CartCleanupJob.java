package com.retail.cart.service;

import com.retail.cart.domain.CartStatus;
import com.retail.cart.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Items go with their cart through the database's ON DELETE CASCADE. */
@Component
public class CartCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CartCleanupJob.class);
    private final CartRepository carts;

    public CartCleanupJob(CartRepository carts) {
        this.carts = carts;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        int expired = carts.deleteExpiredGuestCarts(CartStatus.ACTIVE, now);
        int merged = carts.deleteByStatusOlderThan(CartStatus.MERGED, now.minus(Duration.ofDays(7)));
        log.info("Cart cleanup: removed {} expired guest carts and {} old merged carts", expired, merged);
    }
}
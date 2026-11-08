package com.retail.auth.service;

import com.retail.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshTokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }


    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
        log.info("Removed {} expired refresh tokens", removed);
    }

}


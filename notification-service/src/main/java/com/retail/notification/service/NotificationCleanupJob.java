package com.retail.notification.service;

import com.retail.notification.config.NotificationProperties;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Emails contain personal data, so finished ones are deleted after the retention time. PENDING emails are never deleted. */
@Component
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationLogRepository logs;
    private final NotificationProperties props;

    public NotificationCleanupJob(NotificationLogRepository logs, NotificationProperties props) {
        this.logs = logs;
        this.props = props;
    }

    @Scheduled(cron = "${retail.notification.cleanup-cron:0 20 3 * * *}")
    @Transactional
    public void run() {
        int removed = logs.deleteOlderThan(List.of(NotificationStatus.SENT, NotificationStatus.FAILED),
                Instant.now().minus(props.retention()));
        log.info("Notification cleanup: removed {} old emails", removed);
    }
}
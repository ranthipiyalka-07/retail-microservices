package com.retail.notification.service;

import com.retail.notification.config.NotificationProperties;
import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.mail.EmailSender;
import com.retail.notification.mail.MailDeliveryException;
import com.retail.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationLogRepository logs;
    private final EmailSender sender;
    private final NotificationProperties props;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public NotificationDispatcher(NotificationLogRepository logs, EmailSender sender, NotificationProperties props,
                                  PlatformTransactionManager txManager) {
        this.logs = logs;
        this.sender = sender;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    /** Sends what is due. One broken email never stops the others. Returns how many were sent. */
    public int dispatchDue(int limit) {
        List<Long> ids = readTx.execute(status ->
                logs.findDueIds(NotificationStatus.PENDING, Instant.now(), PageRequest.of(0, limit)));
        int sent = 0;
        if (ids != null) {
            for (Long id : ids) {
                try {
                    if (dispatch(id)) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not process notificationId={}: {}", id, e.toString());
                }
            }
        }
        return sent;
    }

    /**
     * Claim, send, settle. Only the instance whose claim succeeded sends. A crash after the claim only leaves a lease
     * that expires, and the email is then tried again. A crash between sending and marking it SENT can send it twice:
     * email delivery is at-least-once.
     */
    public boolean dispatch(Long id) {
        Instant now = Instant.now();
        boolean claimed = Boolean.TRUE.equals(tx.execute(status ->
                logs.claim(id, NotificationStatus.PENDING, now.plus(props.lease()), now) == 1));
        if (!claimed) {
            return false;
        }
        NotificationLog email = readTx.execute(status -> logs.findById(id).orElse(null));
        if (email == null) {
            return false;
        }

        try {
            sender.send(email.getRecipientEmail(), email.getSubject(), email.getBody());
        } catch (MailDeliveryException e) {
            settleFailure(email, e.getMessage(), e.isPermanent());
            return false;
        } catch (RuntimeException e) {
            settleFailure(email, e.toString(), false);
            return false;
        }

        tx.executeWithoutResult(status ->
                logs.markSent(id, NotificationStatus.PENDING, NotificationStatus.SENT, Instant.now()));
        log.info("Notification sent: notificationId={}, template={}, attempt={}", id, email.getTemplateCode(), email.getAttempts());
        return true;
    }

    private void settleFailure(NotificationLog email, String error, boolean permanent) {
        Instant now = Instant.now();
        String message = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 500));
        boolean giveUp = permanent || email.getAttempts() >= props.maxAttempts();
        tx.executeWithoutResult(status -> {
            if (giveUp) {
                logs.markFailed(email.getId(), NotificationStatus.PENDING, NotificationStatus.FAILED, message, now);
            } else {
                logs.reschedule(email.getId(), NotificationStatus.PENDING,
                        now.plus(props.delayAfterAttempt(email.getAttempts())), message, now);
            }
        });
        log.warn("Notification failed: notificationId={}, attempt={}, {}: {}", email.getId(), email.getAttempts(),
                giveUp ? "giving up (FAILED, an admin can retry it)" : "will retry", message);
    }
}
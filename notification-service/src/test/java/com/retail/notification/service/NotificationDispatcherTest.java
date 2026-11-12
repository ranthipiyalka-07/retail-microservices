package com.retail.notification.service;

import com.retail.notification.config.NotificationProperties;
import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.mail.EmailSender;
import com.retail.notification.mail.MailDeliveryException;
import com.retail.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatcherTest {

    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @Mock NotificationLogRepository logs;
    @Mock EmailSender sender;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        var props = new NotificationProperties("Retail Store", 4,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)), Duration.ofMinutes(2),
                List.of(), List.of(), Duration.ofDays(90), new NotificationProperties.Mail("log", "no-reply@retail.local"));
        dispatcher = new NotificationDispatcher(logs, sender, props, new NoOpTransactionManager());
    }

    // ---------- helpers ----------

    private void claimedEmail(long id, int attemptsAfterClaim) {
        NotificationLog email = new NotificationLog();
        email.setId(id);
        email.setRecipientEmail("john@example.com");
        email.setSubject("Subject");
        email.setBody("Body");
        email.setTemplateCode("CUSTOMER_REGISTERED");
        email.setAttempts(attemptsAfterClaim);
        email.setStatus(NotificationStatus.PENDING);
        when(logs.claim(eq(id), eq(NotificationStatus.PENDING), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(logs.findById(id)).thenReturn(Optional.of(email));
    }

    private Instant nextAttemptOfTheReschedule() {
        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(logs).reschedule(eq(1L), eq(NotificationStatus.PENDING), next.capture(), any(), any(Instant.class));
        return next.getValue();
    }

    // ---------- sending ----------

    @Test
    void aSuccessfulSendMarksTheEmailSent() {
        claimedEmail(1L, 1);

        assertThat(dispatcher.dispatch(1L)).isTrue();

        verify(sender).send("john@example.com", "Subject", "Body");
        verify(logs).markSent(eq(1L), eq(NotificationStatus.PENDING), eq(NotificationStatus.SENT), any(Instant.class));
        verify(logs, never()).reschedule(anyLong(), any(), any(), any(), any());
        verify(logs, never()).markFailed(anyLong(), any(), any(), any(), any());
    }

    @Test
    void anEmailThatAnotherInstanceClaimedIsNotSentTwice() {
        when(logs.claim(eq(1L), eq(NotificationStatus.PENDING), any(Instant.class), any(Instant.class))).thenReturn(0);

        assertThat(dispatcher.dispatch(1L)).isFalse();

        verifyNoInteractions(sender);
        verify(logs, never()).markSent(anyLong(), any(), any(), any());
    }

    // ---------- failing ----------

    @Test
    void aFirstFailureIsRetriedAfterTheFirstDelayAndTheErrorIsRecorded() {
        claimedEmail(1L, 1);
        doThrow(new MailDeliveryException("Connection refused", false, null)).when(sender).send(any(), any(), any());
        Instant before = Instant.now();

        assertThat(dispatcher.dispatch(1L)).isFalse();

        Instant next = nextAttemptOfTheReschedule();
        assertThat(Duration.between(before, next)).isBetween(Duration.ofSeconds(59), Duration.ofSeconds(62));
        verify(logs).reschedule(eq(1L), eq(NotificationStatus.PENDING), any(Instant.class), eq("Connection refused"), any(Instant.class));
        verify(logs, never()).markFailed(anyLong(), any(), any(), any(), any());
    }

    @Test
    void theDelaysGrowWithEachAttempt() {
        claimedEmail(1L, 3);
        doThrow(new MailDeliveryException("timeout", false, null)).when(sender).send(any(), any(), any());
        Instant before = Instant.now();

        dispatcher.dispatch(1L);

        assertThat(Duration.between(before, nextAttemptOfTheReschedule())).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(31));
    }

    @Test
    void afterTheLastAttemptTheEmailFailsForGoodAndWaitsForAnAdmin() {
        claimedEmail(1L, 4);
        doThrow(new MailDeliveryException("timeout", false, null)).when(sender).send(any(), any(), any());

        assertThat(dispatcher.dispatch(1L)).isFalse();

        verify(logs).markFailed(eq(1L), eq(NotificationStatus.PENDING), eq(NotificationStatus.FAILED), eq("timeout"), any(Instant.class));
        verify(logs, never()).reschedule(anyLong(), any(), any(), any(), any());
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        claimedEmail(1L, 1);
        doThrow(new MailDeliveryException("Invalid address", true, null)).when(sender).send(any(), any(), any());

        dispatcher.dispatch(1L);

        verify(logs).markFailed(eq(1L), eq(NotificationStatus.PENDING), eq(NotificationStatus.FAILED), eq("Invalid address"), any(Instant.class));
        verify(logs, never()).reschedule(anyLong(), any(), any(), any(), any());
    }

    @Test
    void anUnexpectedErrorFromTheSenderIsTreatedAsTemporary() {
        claimedEmail(1L, 1);
        doThrow(new IllegalStateException("boom")).when(sender).send(any(), any(), any());

        dispatcher.dispatch(1L);

        verify(logs).reschedule(eq(1L), eq(NotificationStatus.PENDING), any(Instant.class), any(), any(Instant.class));
        verify(logs, never()).markFailed(anyLong(), any(), any(), any(), any());
    }

    // ---------- the job ----------

    @Test
    void oneBrokenEmailDoesNotStopTheOthers() {
        when(logs.findDueIds(eq(NotificationStatus.PENDING), any(Instant.class), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(logs.claim(eq(1L), any(), any(), any())).thenThrow(new RuntimeException("database hiccup"));
        claimedEmail(2L, 1);

        assertThat(dispatcher.dispatchDue(50)).isEqualTo(1);

        verify(sender).send("john@example.com", "Subject", "Body");
    }
}
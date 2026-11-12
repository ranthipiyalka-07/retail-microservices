package com.retail.notification.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.common.event.CustomerRegisteredEvent;
import com.retail.common.event.OrderStatusChangedEvent;
import com.retail.notification.config.NotificationProperties;
import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.domain.NotificationTemplate;
import com.retail.notification.dto.IntakeResult;
import com.retail.notification.exception.InvalidRequestException;
import com.retail.notification.repository.NotificationLogRepository;
import com.retail.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationIntakeTest {

    /** Transactions do nothing in these tests: only the intake logic is checked. */
    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    private static final UUID EVENT_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock NotificationTemplateRepository templates;
    @Mock NotificationLogRepository logs;

    private NotificationIntake intake;

    @BeforeEach
    void setUp() {
        var props = new NotificationProperties("Retail Store", 4, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(2),
                List.of("SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"), List.of(), Duration.ofDays(90),
                new NotificationProperties.Mail("log", "no-reply@retail.local"));
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false
                );

        intake = new NotificationIntake(
                new NotificationFactory(props),
                new TemplateRenderer(),
                templates,
                logs,
                mapper,
                new NoOpTransactionManager()
        );

        when(logs.saveAndFlush(any(NotificationLog.class))).thenAnswer(returnsFirstArg());
        when(logs.existsByEventIdAndRecipientEmail(any(), any())).thenReturn(false);
        when(templates.findByCode("CUSTOMER_REGISTERED")).thenReturn(Optional.of(
                template("CUSTOMER_REGISTERED", "Welcome to {{shopName}}", "Hello {{firstName}}", true)));
    }

    private static NotificationTemplate template(String code, String subject, String body, boolean active) {
        NotificationTemplate t = new NotificationTemplate();
        t.setCode(code);
        t.setChannel("EMAIL");
        t.setSubject(subject);
        t.setBody(body);
        t.setPlaceholders("firstName,email,shopName");
        t.setActive(active);
        return t;
    }

    private static CustomerRegisteredEvent registered(String email) {
        return new CustomerRegisteredEvent(EVENT_ID, Instant.now(), 3L, email, "John");
    }

    // ---------- storing ----------

    @Test
    void storesThePendingEmailWithTheRenderedTextAndTheNormalizedAddress() {
        IntakeResult result = intake.accept(registered(" John@Example.com "));

        assertThat(result).isEqualTo(new IntakeResult(1, 0, 0));
        ArgumentCaptor<NotificationLog> row = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logs).saveAndFlush(row.capture());
        assertThat(row.getValue().getRecipientEmail()).isEqualTo("john@example.com");
        assertThat(row.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.getValue().getSubject()).isEqualTo("Welcome to Retail Store");
        assertThat(row.getValue().getBody()).isEqualTo("Hello John");
        assertThat(row.getValue().getEventId()).isEqualTo(EVENT_ID.toString());
        assertThat(row.getValue().getEventType()).isEqualTo("CUSTOMER_REGISTERED");
        assertThat(row.getValue().getAttempts()).isZero();
        assertThat(row.getValue().getNextAttemptAt()).isNotNull();
        assertThat(row.getValue().getReferenceType()).isEqualTo("CUSTOMER");
        assertThat(row.getValue().getReferenceId()).isEqualTo("3");
    }

    @Test
    void anEventThatWasAlreadyStoredForTheSameAddressIsNotStoredAgain() {
        when(logs.existsByEventIdAndRecipientEmail(EVENT_ID.toString(), "john@example.com")).thenReturn(true);

        IntakeResult result = intake.accept(registered("john@example.com"));

        assertThat(result).isEqualTo(new IntakeResult(0, 1, 0));
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void aParallelDeliveryOfTheSameEventIsRecognizedAsADuplicate() {
        when(logs.existsByEventIdAndRecipientEmail(any(), any())).thenReturn(false, true);
        when(logs.saveAndFlush(any(NotificationLog.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(intake.accept(registered("john@example.com"))).isEqualTo(new IntakeResult(0, 1, 0));
    }

    @Test
    void aDatabaseErrorThatIsNotADuplicateIsNotSwallowed() {
        when(logs.saveAndFlush(any(NotificationLog.class))).thenThrow(new DataIntegrityViolationException("data too long"));

        assertThatThrownBy(() -> intake.accept(registered("john@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInvalidAddressIsStoredAsFailedSoAnAdminCanSeeIt() {
        IntakeResult result = intake.accept(registered("not an email,\r\nbcc:x@example.com"));

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<NotificationLog> row = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logs).saveAndFlush(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(row.getValue().getLastError()).isEqualTo("INVALID_RECIPIENT");
        assertThat(row.getValue().getRecipientEmail()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void aMissingAddressIsStoredAsFailedToo() {
        intake.accept(registered(null));

        ArgumentCaptor<NotificationLog> row = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logs).saveAndFlush(row.capture());
        assertThat(row.getValue().getRecipientEmail()).isEqualTo("(missing)");
        assertThat(row.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void aMissingOrSwitchedOffTemplateMeansNoEmail() {
        when(templates.findByCode("CUSTOMER_REGISTERED")).thenReturn(Optional.empty());
        assertThat(intake.accept(registered("john@example.com"))).isEqualTo(new IntakeResult(0, 0, 1));

        when(templates.findByCode("CUSTOMER_REGISTERED")).thenReturn(Optional.of(
                template("CUSTOMER_REGISTERED", "S", "B", false)));
        assertThat(intake.accept(registered("john@example.com"))).isEqualTo(new IntakeResult(0, 0, 1));
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void anEventThatNeedsNoEmailChangesNothing() {
        var packed = new OrderStatusChangedEvent(UUID.randomUUID(), Instant.now(), 7L, "ORD-1", 3L, "john@example.com",
                "CONFIRMED", "PACKED", null);

        assertThat(intake.accept(packed)).isEqualTo(new IntakeResult(0, 0, 0));
        verify(logs, never()).saveAndFlush(any());
    }

    // ---------- JSON ----------

    @Test
    void readsAnEventFromJsonLikeTheOtherServicesSendIt() throws Exception {
        var payload = new ObjectMapper().readTree("""
                {"eventId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","occurredAt":"2026-11-10T10:00:00.123456Z",
                 "customerId":3,"email":"john@example.com","firstName":"John","somethingNew":true}""");

        IntakeResult result = intake.acceptJson("customer_registered", payload);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<NotificationLog> row = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logs).saveAndFlush(row.capture());
        assertThat(row.getValue().getEventId()).isEqualTo(EVENT_ID.toString());
    }

    @Test
    void anUnknownTypeAMalformedPayloadAndAMissingEventIdAreRefused() throws Exception {
        var mapper = new ObjectMapper();

        assertThatThrownBy(() -> intake.acceptJson("BOGUS", mapper.readTree("{}")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("Unknown event type");
        assertThatThrownBy(() -> intake.acceptJson("CUSTOMER_REGISTERED", mapper.readTree("\"just text\"")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not a valid");
        assertThatThrownBy(() -> intake.acceptJson("CUSTOMER_REGISTERED",
                mapper.readTree("{\"customerId\":3,\"email\":\"john@example.com\"}")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("eventId");
        verify(logs, never()).saveAndFlush(any());
    }
}
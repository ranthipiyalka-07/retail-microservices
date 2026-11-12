package com.retail.notification.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.domain.NotificationTemplate;
import com.retail.notification.dto.NotificationDetail;
import com.retail.notification.dto.PageResponse;
import com.retail.notification.dto.NotificationSummary;
import com.retail.notification.dto.TemplateResponse;
import com.retail.notification.dto.TemplateUpdateRequest;
import com.retail.notification.exception.ConflictException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationAdminServiceTest {

    @Mock NotificationLogRepository logs;
    @Mock NotificationTemplateRepository templates;

    private NotificationAdminService service;

    @BeforeEach
    void setUp() {
        service = new NotificationAdminService(logs, templates);
    }

    private static NotificationLog email(long id, NotificationStatus status) {
        NotificationLog n = new NotificationLog();
        n.setId(id);
        n.setEventId("e-1");
        n.setEventType("CUSTOMER_REGISTERED");
        n.setRecipientEmail("john@example.com");
        n.setTemplateCode("CUSTOMER_REGISTERED");
        n.setSubject("Welcome");
        n.setBody("Hello");
        n.setStatus(status);
        return n;
    }

    private NotificationTemplate welcomeTemplate() {
        NotificationTemplate t = new NotificationTemplate();
        t.setCode("CUSTOMER_REGISTERED");
        t.setChannel("EMAIL");
        t.setSubject("Welcome to {{shopName}}");
        t.setBody("Hello {{firstName}}");
        t.setPlaceholders("firstName,email,shopName");
        t.setActive(true);
        when(templates.findByCode("CUSTOMER_REGISTERED")).thenReturn(Optional.of(t));
        return t;
    }

    // ---------- the log ----------

    @Test
    void retryingAFailedEmailPutsItBackToPending() {
        when(logs.retry(eq(5L), eq(NotificationStatus.FAILED), eq(NotificationStatus.PENDING), any(Instant.class))).thenReturn(1);
        when(logs.findById(5L)).thenReturn(Optional.of(email(5L, NotificationStatus.PENDING)));

        NotificationDetail detail = service.retry(5L);

        assertThat(detail.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(detail.body()).isEqualTo("Hello");
    }

    @Test
    void onlyAFailedEmailCanBeRetriedAndAnUnknownOneIsNotFound() {
        when(logs.retry(eq(5L), any(), any(), any(Instant.class))).thenReturn(0);
        when(logs.existsById(5L)).thenReturn(true);
        when(logs.retry(eq(6L), any(), any(), any(Instant.class))).thenReturn(0);
        when(logs.existsById(6L)).thenReturn(false);

        assertThatThrownBy(() -> service.retry(5L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.retry(6L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theListIsNewestFirstFilteredByStatusAndClampsThePageSize() {
        when(logs.findByStatus(eq(NotificationStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(email(9L, NotificationStatus.FAILED)), PageRequest.of(0, 100), 1));

        PageResponse<NotificationSummary> result = service.list(NotificationStatus.FAILED, -2, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(logs).findByStatus(eq(NotificationStatus.FAILED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        assertThat(result.content().get(0).recipient()).isEqualTo("john@example.com");
    }

    @Test
    void anUnknownNotificationIsNotFound() {
        when(logs.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- templates ----------

    @Test
    void aTemplateCanBeChangedWithTheAllowedPlaceholdersAndSwitchedOff() {
        NotificationTemplate t = welcomeTemplate();

        TemplateResponse response = service.updateTemplate("CUSTOMER_REGISTERED",
                new TemplateUpdateRequest("  Hi {{ firstName }}\r\n", "Hello {{firstName}},\r\nWelcome to {{shopName}}", false),
                "admin@retail.local");

        assertThat(response.subject()).isEqualTo("Hi {{ firstName }}");
        assertThat(response.body()).isEqualTo("Hello {{firstName}},\nWelcome to {{shopName}}");
        assertThat(response.active()).isFalse();
        assertThat(t.getUpdatedBy()).isEqualTo("admin@retail.local");
        assertThat(response.placeholders()).containsExactly("firstName", "email", "shopName");
    }

    @Test
    void leavingTheActiveFlagOutKeepsItAsItWas() {
        NotificationTemplate t = welcomeTemplate();

        service.updateTemplate("CUSTOMER_REGISTERED", new TemplateUpdateRequest("Hi", "Hello", null), "admin@retail.local");

        assertThat(t.isActive()).isTrue();
    }

    @Test
    void aPlaceholderTheTemplateWasNotDesignedForIsRefused() {
        welcomeTemplate();

        assertThatThrownBy(() -> service.updateTemplate("CUSTOMER_REGISTERED",
                new TemplateUpdateRequest("Hi", "Hello {{customerName}} {{shopName}}", null), "admin@retail.local"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown placeholders: customerName");
    }

    @Test
    void aSubjectOrBodyWithoutVisibleTextIsRefusedAndAnUnknownTemplateIsNotFound() {
        welcomeTemplate();
        when(templates.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate("CUSTOMER_REGISTERED",
                new TemplateUpdateRequest("\u0001 \u0002", "Hello", null), "admin@retail.local"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.updateTemplate("NOPE",
                new TemplateUpdateRequest("Hi", "Hello", null), "admin@retail.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
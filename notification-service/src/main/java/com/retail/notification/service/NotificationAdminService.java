package com.retail.notification.service;

import com.retail.common.audit.Audit;
import com.retail.common.error.ResourceNotFoundException;
import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.domain.NotificationTemplate;
import com.retail.notification.dto.*;
import com.retail.notification.exception.ConflictException;
import com.retail.notification.exception.InvalidRequestException;
import com.retail.notification.repository.NotificationLogRepository;
import com.retail.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationAdminService {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdminService.class);

    private final NotificationLogRepository logs;
    private final NotificationTemplateRepository templates;

    public NotificationAdminService(NotificationLogRepository logs, NotificationTemplateRepository templates) {
        this.logs = logs;
        this.templates = templates;
    }

    // ================= the email log =================

    @Transactional(readOnly = true)
    public PageResponse<NotificationSummary> list(NotificationStatus status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of((status == null ? logs.findAll(pageable) : logs.findByStatus(status, pageable))
                .map(NotificationAdminService::toSummary));
    }

    @Transactional(readOnly = true)
    public NotificationDetail get(Long id) {
        return toDetail(logs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", id)));
    }

    /** Only a FAILED email can be retried. It goes back to PENDING with a fresh set of attempts. */
    @Transactional
    public NotificationDetail retry(Long id) {
        if (logs.retry(id, NotificationStatus.FAILED, NotificationStatus.PENDING, Instant.now()) == 0) {
            if (!logs.existsById(id)) {
                throw new ResourceNotFoundException("Notification", id);
            }
            throw new ConflictException("Only a FAILED notification can be retried");
        }
        log.info("Notification queued for retry: notificationId={}", id);
        return toDetail(logs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", id)));
    }

    // ================= templates =================

    @Transactional(readOnly = true)
    public List<TemplateResponse> templates() {
        return templates.findAllByOrderByCodeAsc().stream().map(NotificationAdminService::toResponse).toList();
    }

    /** Only the placeholders a template was designed for may be used: a typo would otherwise send blank text. */
    @Transactional
    public TemplateResponse updateTemplate(String code, TemplateUpdateRequest request, String actor) {
        NotificationTemplate template = templates.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Template", code));

        String subject = Text.line(request.subject(), 200);
        String body = Text.body(request.body()).trim();
        if (subject.isEmpty() || body.isEmpty()) {
            throw new InvalidRequestException("The subject and the body must contain visible characters");
        }
        Set<String> unknown = new LinkedHashSet<>(TemplateRenderer.placeholdersIn(subject));
        unknown.addAll(TemplateRenderer.placeholdersIn(body));
        unknown.removeAll(allowed(template));
        if (!unknown.isEmpty()) {
            throw new InvalidRequestException("Unknown placeholders: " + String.join(", ", unknown)
                    + ". Allowed: " + String.join(", ", allowed(template)));
        }

        template.setSubject(subject);
        template.setBody(body);
        if (request.active() != null) {
            template.setActive(request.active());
        }
        template.setUpdatedAt(Instant.now());
        template.setUpdatedBy(actor);
        log.info("Template updated: code={}, active={}, by={}", code, template.isActive(), actor);
        Audit.record("notification.template.update", Audit.Outcome.SUCCESS, "template", code, "active=" + template.isActive());
        return toResponse(template);
    }

    // ================= mapping =================

    private static Set<String> allowed(NotificationTemplate t) {
        return Arrays.stream(t.getPlaceholders().split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static TemplateResponse toResponse(NotificationTemplate t) {
        return new TemplateResponse(t.getCode(), t.getChannel(), t.getSubject(), t.getBody(),
                List.copyOf(allowed(t)), t.isActive(), t.getUpdatedAt(), t.getUpdatedBy());
    }

    private static NotificationSummary toSummary(NotificationLog n) {
        return new NotificationSummary(n.getId(), n.getEventType(), n.getRecipientEmail(), n.getTemplateCode(),
                n.getSubject(), n.getStatus(), n.getAttempts(), n.getLastError(), n.getReferenceType(),
                n.getReferenceId(), n.getCreatedAt(), n.getSentAt(), n.getNextAttemptAt());
    }

    private static NotificationDetail toDetail(NotificationLog n) {
        return new NotificationDetail(n.getId(), n.getEventId(), n.getEventType(), n.getRecipientEmail(),
                n.getTemplateCode(), n.getSubject(), n.getBody(), n.getStatus(), n.getAttempts(), n.getLastError(),
                n.getReferenceType(), n.getReferenceId(), n.getCreatedAt(), n.getSentAt(), n.getNextAttemptAt());
    }
}
package com.retail.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.common.event.DomainEvent;
import com.retail.notification.domain.EventType;
import com.retail.notification.domain.NotificationLog;
import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.domain.NotificationTemplate;
import com.retail.notification.dto.IntakeResult;
import com.retail.notification.exception.InvalidRequestException;
import com.retail.notification.repository.NotificationLogRepository;
import com.retail.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where events come in. A Kafka listener will later call accept(event) too. Sending is NOT done here: the email
 * is stored as PENDING and the dispatch job sends it, so this call is fast and never fails because of the mail server.
 */
@Service
public class NotificationIntake {

    private static final Logger log = LoggerFactory.getLogger(NotificationIntake.class);

    private final NotificationFactory factory;
    private final TemplateRenderer renderer;
    private final NotificationTemplateRepository templates;
    private final NotificationLogRepository logs;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public NotificationIntake(NotificationFactory factory, TemplateRenderer renderer,
                              NotificationTemplateRepository templates, NotificationLogRepository logs,
                              ObjectMapper mapper, PlatformTransactionManager txManager) {
        this.factory = factory;
        this.renderer = renderer;
        this.templates = templates;
        this.logs = logs;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    /** For the HTTP endpoint: reads the JSON into the event class of the given type. */
    public IntakeResult acceptJson(String type, JsonNode payload) {
        EventType eventType = EventType.parse(type)
                .orElseThrow(() -> new InvalidRequestException("Unknown event type '" + type + "'"));
        DomainEvent event;
        try {
            event = mapper.treeToValue(payload, eventType.payloadType());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidRequestException("The payload is not a valid " + eventType + " event");
        }
        if (event == null) {
            throw new InvalidRequestException("The payload is not a valid " + eventType + " event");
        }
        return accept(event);
    }

    public IntakeResult accept(DomainEvent event) {
        if (event.eventId() == null) {
            throw new InvalidRequestException("eventId is required");
        }
        int created = 0;
        int duplicates = 0;
        int skipped = 0;

        for (Draft draft : factory.drafts(event)) {
            Optional<NotificationTemplate> template = templates.findByCode(draft.templateCode());
            if (template.isEmpty() || !template.get().isActive()) {
                log.info("No email for event {}: template {} is missing or switched off", draft.eventId(), draft.templateCode());
                skipped++;
                continue;
            }
            TemplateRenderer.Rendered rendered = renderer.render(template.get().getSubject(), template.get().getBody(), draft.vars());

            boolean valid = Recipients.isValid(draft.recipient() == null ? null : draft.recipient().trim());
            Instant now = Instant.now();
            NotificationLog row = new NotificationLog();
            row.setEventId(draft.eventId().toString());
            row.setEventType(draft.eventType().name());
            row.setRecipientEmail(recipientFor(draft.recipient()));
            row.setTemplateCode(draft.templateCode());
            row.setSubject(rendered.subject());
            row.setBody(rendered.body());
            row.setStatus(valid ? NotificationStatus.PENDING : NotificationStatus.FAILED);
            row.setLastError(valid ? null : "INVALID_RECIPIENT");
            row.setNextAttemptAt(now);
            row.setReferenceType(draft.referenceType());
            row.setReferenceId(draft.referenceId());

            if (store(row)) {
                created++;
                if (!valid) {
                    log.warn("Event {} has an invalid recipient address: the email is stored as FAILED", draft.eventId());
                }
            } else {
                duplicates++;
            }
        }
        return new IntakeResult(created, duplicates, skipped);
    }

    /** true = stored, false = the same event for the same address was already known. */
    private boolean store(NotificationLog row) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                if (logs.existsByEventIdAndRecipientEmail(row.getEventId(), row.getRecipientEmail())) {
                    return false;
                }
                logs.saveAndFlush(row);
                return true;
            }));
        } catch (DataIntegrityViolationException e) {
            if (logs.existsByEventIdAndRecipientEmail(row.getEventId(), row.getRecipientEmail())) {
                return false;                                        // a parallel delivery of the same event won
            }
            throw e;
        }
    }

    /** What is stored in the recipient column. A broken address is kept readable for the admin, but cleaned. */
    private static String recipientFor(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "(missing)";
        }
        String normalized = Recipients.normalize(recipient);
        return Recipients.isValid(normalized) ? normalized : Text.line(normalized, 255);
    }
}
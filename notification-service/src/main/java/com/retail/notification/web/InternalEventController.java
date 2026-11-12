package com.retail.notification.web;

import com.retail.notification.dto.EventEnvelope;
import com.retail.notification.dto.IntakeResult;
import com.retail.notification.service.NotificationIntake;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/notifications")
public class InternalEventController {

    private final NotificationIntake intake;

    public InternalEventController(NotificationIntake intake) {
        this.intake = intake;
    }

    /** 202: the emails are stored and will be sent by the dispatch job. */
    @PostMapping("/events")
    public ResponseEntity<IntakeResult> receive(@Valid @RequestBody EventEnvelope envelope) {
        return ResponseEntity.accepted().body(intake.acceptJson(envelope.type(), envelope.payload()));
    }
}
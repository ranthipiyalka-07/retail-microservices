package com.retail.notification.service;

import com.retail.notification.domain.EventType;

import java.util.Map;
import java.util.UUID;

/** One email that an event asks for, before the template is applied. vars are already cleaned. */
public record Draft(UUID eventId, EventType eventType, String recipient, String templateCode,
                    Map<String, String> vars, String referenceType, String referenceId) {}
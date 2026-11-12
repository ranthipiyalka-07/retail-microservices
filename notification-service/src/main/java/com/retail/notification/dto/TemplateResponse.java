package com.retail.notification.dto;

import java.time.Instant;
import java.util.List;

public record TemplateResponse(String code, String channel, String subject, String body, List<String> placeholders,
                               boolean active, Instant updatedAt, String updatedBy) {}
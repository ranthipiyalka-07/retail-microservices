package com.retail.notification.dto;

/** created = stored for sending, duplicates = already known, skipped = no template, template switched off, or nothing to send. */
public record IntakeResult(int created, int duplicates, int skipped) {}
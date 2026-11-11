package com.retail.support.dto;

import com.retail.support.domain.SenderType;

import java.time.Instant;

/** Customers see "CUSTOMER" or "AGENT": never an agent's email. */
public record CustomerMessage(Long id, SenderType sender, String message, Instant createdAt) {}
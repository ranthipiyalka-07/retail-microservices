package com.retail.support.dto;

import com.retail.support.domain.SenderType;

import java.time.Instant;

public record AgentMessage(Long id, SenderType sender, String author, boolean internal, String message,
                           Instant createdAt) {}
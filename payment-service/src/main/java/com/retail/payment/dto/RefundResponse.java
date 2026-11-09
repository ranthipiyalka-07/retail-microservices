package com.retail.payment.dto;

import com.retail.payment.domain.PaymentStatus;
import com.retail.payment.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(Long id, Long paymentId, BigDecimal amount, String reason, RefundStatus status,
                             String failureReason, PaymentStatus paymentStatus, Instant createdAt) {}
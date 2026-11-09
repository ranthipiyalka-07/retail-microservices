package com.retail.payment.dto;

import com.retail.payment.domain.PaymentMethod;
import com.retail.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, Long customerId, BigDecimal amount, String currency,
                              PaymentMethod method, PaymentStatus status, String cardLast4, String failureReason,
                              BigDecimal refundedAmount, Instant createdAt) {}
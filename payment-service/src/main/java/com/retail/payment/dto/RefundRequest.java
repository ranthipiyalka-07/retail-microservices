package com.retail.payment.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RefundRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "may contain letters, digits, '.', '_', ':' and '-' only")
        String idempotencyKey,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,   // empty = refund everything still refundable
        @NotBlank @Size(max = 200) String reason) {}
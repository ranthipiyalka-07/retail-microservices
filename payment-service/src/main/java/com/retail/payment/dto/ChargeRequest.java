package com.retail.payment.dto;

import com.retail.payment.domain.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ChargeRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long customerId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String currency,
        @NotNull PaymentMethod method,
        @NotBlank @Size(max = 255) String paymentToken,        // produced by the gateway's hosted form: never a card number
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "may contain letters, digits, '.', '_', ':' and '-' only")
        String idempotencyKey) {

    @Override
    public String toString() {                                  // the token is a credential: never print it
        return "ChargeRequest[orderId=" + orderId + ", method=" + method + "]";
    }
}
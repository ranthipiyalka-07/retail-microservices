package com.retail.payment.gateway;

import com.retail.payment.domain.PaymentMethod;

import java.math.BigDecimal;

public record ChargeCommand(String idempotencyKey, Long orderId, BigDecimal amount, String currency,
                            PaymentMethod method, String paymentToken) {
    @Override
    public String toString() {                                 // the token is a credential: never print it
        return "ChargeCommand[orderId=" + orderId + ", amount=" + amount + " " + currency + "]";
    }
}
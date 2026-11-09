package com.retail.payment.gateway;

import java.math.BigDecimal;

public record RefundCommand(String idempotencyKey, String chargeReference, BigDecimal amount, String reason) {}
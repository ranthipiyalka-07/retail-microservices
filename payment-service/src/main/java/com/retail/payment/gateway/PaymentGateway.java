package com.retail.payment.gateway;

import java.util.Optional;

/**
 * The only class of this service that talks to a payment provider. Contract for implementations:
 * a decline is a normal result (approved = false); GatewayUnavailableException means the outcome is UNKNOWN
 * (timeout, outage). Every operation must be idempotent for the same idempotency key.
 */
public interface PaymentGateway {
    GatewayCharge charge(ChargeCommand command);
    Optional<GatewayCharge> findCharge(String idempotencyKey);
    GatewayRefund refund(RefundCommand command);
    Optional<GatewayRefund> findRefund(String idempotencyKey);
}
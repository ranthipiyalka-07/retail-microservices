package com.retail.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
public class GatewayAccess {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccess.class);

    private final PaymentGateway gateway;
    private final CircuitBreakerFactory<?, ?> breakers;

    public GatewayAccess(PaymentGateway gateway, CircuitBreakerFactory<?, ?> breakers) {
        this.gateway = gateway;
        this.breakers = breakers;
    }

    public GatewayCharge charge(ChargeCommand command) {
        return call(() -> gateway.charge(command));
    }

    public Optional<GatewayCharge> findCharge(String idempotencyKey) {
        return call(() -> gateway.findCharge(idempotencyKey));
    }

    public GatewayRefund refund(RefundCommand command) {
        return call(() -> gateway.refund(command));
    }

    public Optional<GatewayRefund> findRefund(String idempotencyKey) {
        return call(() -> gateway.findRefund(idempotencyKey));
    }

    private <T> T call(Supplier<T> action) {
        CircuitBreaker breaker = breakers.create("payment-gateway");
        return breaker.run(action, throwable -> {
            log.warn("Payment gateway call failed: {}", throwable.toString());
            throw new GatewayUnavailableException("Payment gateway unavailable", throwable);
        });
    }
}
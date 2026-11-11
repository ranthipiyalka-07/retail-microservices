package com.retail.support.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderVerifier {

    private static final Logger log = LoggerFactory.getLogger(OrderVerifier.class);

    public enum Result { OWNED, NOT_OWNED, UNKNOWN }

    private final OrderClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String internalKey;

    public OrderVerifier(OrderClient client, CircuitBreakerFactory<?, ?> breakers,
                         @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.breakers = breakers;
        this.internalKey = internalKey;
    }

    /**
     * UNKNOWN means order-service could not be asked: the ticket is still accepted (a support channel must work
     * when something is broken) and is marked unverified for the agent. A 404 is a normal answer, not a failure.
     */
    public Result check(Long customerId, Long orderId) {
        CircuitBreaker breaker = breakers.create("order-service");
        return breaker.run(() -> {
            try {
                client.ownership(internalKey, orderId, customerId);
                return Result.OWNED;
            } catch (FeignException.NotFound e) {
                return Result.NOT_OWNED;
            }
        }, throwable -> {
            log.warn("order-service could not verify orderId={} for customerId={}: {}", orderId, customerId, throwable.toString());
            return Result.UNKNOWN;
        });
    }
}
package com.retail.review.client;

import com.retail.review.exception.OrderServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchaseLookup {

    private static final Logger log = LoggerFactory.getLogger(PurchaseLookup.class);

    /** One order of the customer that contains the product. */
    public record Purchase(Long orderId, String status) {}

    private final OrderClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String internalKey;

    public PurchaseLookup(OrderClient client, CircuitBreakerFactory<?, ?> breakers,
                          @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.breakers = breakers;
        this.internalKey = internalKey;
    }

    public List<Purchase> find(Long customerId, Long productId) {
        CircuitBreaker breaker = breakers.create("order-service");
        return breaker.run(
                () -> client.purchases(internalKey, customerId, productId).stream()
                        .map(p -> new Purchase(p.orderId(), p.status())).toList(),
                throwable -> {
                    log.warn("order-service call failed for customerId={}, productId={}: {}",
                            customerId, productId, throwable.toString());
                    throw new OrderServiceUnavailableException();
                });
    }
}
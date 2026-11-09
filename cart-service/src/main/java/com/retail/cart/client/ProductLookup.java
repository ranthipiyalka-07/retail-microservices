package com.retail.cart.client;

import com.retail.cart.exception.ProductServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProductLookup {

    private static final Logger log = LoggerFactory.getLogger(ProductLookup.class);

    private final ProductClient client;
    private final CircuitBreakerFactory<?, ?> breakers;

    public ProductLookup(ProductClient client, CircuitBreakerFactory<?, ?> breakers) {
        this.client = client;
        this.breakers = breakers;
    }

    /**
     * Empty means "no such (active) product". A 404 is answered inside the protected call, so it is a normal
     * result and never counts as a failure for the circuit breaker. Real failures give a 503.
     */
    public Optional<ProductInfo> find(Long productId) {
        CircuitBreaker breaker = breakers.create("product-service");
        return breaker.run(() -> fetch(productId), throwable -> {
            log.warn("product-service call failed for productId={}: {}", productId, throwable.toString());
            throw new ProductServiceUnavailableException();
        });
    }

    private Optional<ProductInfo> fetch(Long productId) {
        try {
            return Optional.ofNullable(client.getProduct(productId));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }
}
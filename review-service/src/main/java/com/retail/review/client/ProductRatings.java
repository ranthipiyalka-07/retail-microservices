package com.retail.review.client;

import com.retail.review.exception.ProductSyncException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProductRatings {

    private static final Logger log = LoggerFactory.getLogger(ProductRatings.class);

    private final ProductClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String internalKey;

    public ProductRatings(ProductClient client, CircuitBreakerFactory<?, ?> breakers,
                          @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.breakers = breakers;
        this.internalKey = internalKey;
    }

    /**
     * Returns normally when product-service has stored the summary, or when it does not know the product
     * (nothing to update there). Anything else is a technical failure: ProductSyncException, retried later.
     */
    public void push(Long productId, BigDecimal average, int reviewCount, long version) {
        CircuitBreaker breaker = breakers.create("product-service");
        breaker.run(() -> {
            try {
                client.updateRating(internalKey, productId, new ProductClient.RatingUpdate(average, reviewCount, version));
            } catch (FeignException.NotFound e) {
                log.warn("product-service does not know productId={}: nothing to update there", productId);
            }
            return Boolean.TRUE;
        }, throwable -> {
            throw new ProductSyncException(throwable);
        });
    }
}
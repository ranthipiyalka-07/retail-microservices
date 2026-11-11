package com.retail.wishlist.client;

import com.retail.wishlist.dto.ProductCard;
import com.retail.wishlist.exception.RemoteUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductCards {

    private static final Logger log = LoggerFactory.getLogger(ProductCards.class);

    private final ProductClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String internalKey;

    public ProductCards(ProductClient client, CircuitBreakerFactory<?, ?> breakers,
                        @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.breakers = breakers;
        this.internalKey = internalKey;
    }

    /**
     * One call for the whole page. Products that are missing from the answer are not available (unknown,
     * inactive or deleted). A technical failure is a 503: a wishlist without product details is not useful.
     */
    public Map<Long, ProductCard> cards(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        CircuitBreaker breaker = breakers.create("product-service");
        return breaker.run(() -> {
            Map<Long, ProductCard> byProduct = new HashMap<>();
            List<ProductClient.ProductData> data = client.summaries(internalKey, productIds);
            if (data != null) {
                data.forEach(d -> byProduct.putIfAbsent(d.id(), toCard(d)));
            }
            return byProduct;
        }, throwable -> {
            log.warn("product-service call failed: {}", throwable.toString());
            throw new RemoteUnavailableException("product");
        });
    }

    private static ProductCard toCard(ProductClient.ProductData d) {
        BigDecimal effective = d.effectivePrice() != null ? d.effectivePrice() : d.basePrice();
        BigDecimal discount = d.discountAmount() != null ? d.discountAmount() : BigDecimal.ZERO;
        return new ProductCard(d.name(), d.brand(), d.basePrice(), effective, discount, d.campaignName(),
                d.avgRating(), d.reviewCount() == null ? 0 : d.reviewCount(), d.primaryImageUrl());
    }
}
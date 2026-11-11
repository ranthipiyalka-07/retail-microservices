package com.retail.product.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CampaignPricing {

    private static final Logger log = LoggerFactory.getLogger(CampaignPricing.class);

    private final CampaignClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String internalKey;

    public CampaignPricing(CampaignClient client, CircuitBreakerFactory<?, ?> breakers,
                           @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.breakers = breakers;
        this.internalKey = internalKey;
    }

    /**
     * Shows campaign prices on the catalog. If campaign-service is down, the answer is empty and the catalog shows
     * base prices: a display fallback only. The order service is the one that decides what is charged.
     */
    public Map<Long, CampaignClient.PriceData> quote(List<CampaignClient.PriceQuery.Item> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        CircuitBreaker breaker = breakers.create("campaign-service");
        return breaker.run(() -> {
            Map<Long, CampaignClient.PriceData> byProduct = new HashMap<>();
            List<CampaignClient.PriceData> data = client.effective(internalKey, new CampaignClient.PriceQuery(items));
            if (data != null) {
                data.forEach(d -> byProduct.putIfAbsent(d.productId(), d));
            }
            return byProduct;
        }, throwable -> {
            log.warn("campaign-service unavailable, showing base prices: {}", throwable.toString());
            return Map.of();
        });
    }
}
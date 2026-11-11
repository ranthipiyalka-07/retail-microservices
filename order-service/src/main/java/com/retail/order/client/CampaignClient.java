package com.retail.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "campaign-service")
public interface CampaignClient {

    @PostMapping("/internal/prices/effective")
    List<PriceData> effective(@RequestHeader("X-Internal-Key") String key, @RequestBody PriceQuery query);

    record PriceQuery(List<Item> items) {
        public record Item(Long productId, Long categoryId, BigDecimal basePrice) {}
    }

    /** discountAmount is per unit. */
    record PriceData(Long productId, BigDecimal basePrice, BigDecimal effectivePrice, BigDecimal discountAmount,
                     Long campaignId, String campaignName) {}
}
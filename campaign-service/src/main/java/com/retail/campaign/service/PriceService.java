package com.retail.campaign.service;

import com.retail.campaign.domain.TargetType;
import com.retail.campaign.dto.PriceQueryItem;
import com.retail.campaign.dto.PriceResponse;
import com.retail.campaign.repository.ActiveOffer;
import com.retail.campaign.repository.CampaignTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PriceService {

    private static final List<Long> NO_IDS = List.of(-1L);      // an empty IN list is not portable: use an id that cannot exist

    private final CampaignTargetRepository targets;
    private final PriceEngine engine;

    public PriceService(CampaignTargetRepository targets, PriceEngine engine) {
        this.targets = targets;
        this.engine = engine;
    }

    /** One indexed query for the whole batch. The answer has one entry per item, in the same order. */
    @Transactional(readOnly = true)
    public List<PriceResponse> effectivePrices(List<PriceQueryItem> items) {
        Set<Long> productIds = new LinkedHashSet<>();
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (PriceQueryItem item : items) {
            productIds.add(item.productId());
            if (item.categoryId() != null) {
                categoryIds.add(item.categoryId());
            }
        }

        List<ActiveOffer> offers = targets.findActiveOffers(CampaignService.OPEN, Instant.now(),
                TargetType.PRODUCT, nonEmpty(productIds), TargetType.CATEGORY, nonEmpty(categoryIds));
        return items.stream().map(item -> engine.price(item, offers)).toList();
    }

    private static Collection<Long> nonEmpty(Set<Long> ids) {
        return ids.isEmpty() ? NO_IDS : ids;
    }
}
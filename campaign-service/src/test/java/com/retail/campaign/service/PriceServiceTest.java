package com.retail.campaign.service;

import com.retail.campaign.domain.DiscountType;
import com.retail.campaign.domain.TargetType;
import com.retail.campaign.dto.PriceQueryItem;
import com.retail.campaign.dto.PriceResponse;
import com.retail.campaign.repository.ActiveOffer;
import com.retail.campaign.repository.CampaignTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceServiceTest {

    @Mock CampaignTargetRepository targets;
    @Captor ArgumentCaptor<Collection<Long>> productIds;
    @Captor ArgumentCaptor<Collection<Long>> categoryIds;

    private PriceService service;

    @BeforeEach
    void setUp() {
        service = new PriceService(targets, new PriceEngine());
    }

    private static PriceQueryItem item(long productId, Long categoryId, String price) {
        return new PriceQueryItem(productId, categoryId, new BigDecimal(price));
    }

    @Test
    void pricesEveryItemInTheOrderOfTheRequest() {
        when(targets.findActiveOffers(any(), any(Instant.class), eq(TargetType.PRODUCT), anyCollection(),
                eq(TargetType.CATEGORY), anyCollection()))
                .thenReturn(List.of(new ActiveOffer(3L, "Sale", DiscountType.PERCENTAGE, new BigDecimal("10"),
                        TargetType.PRODUCT, 2L)));

        List<PriceResponse> result = service.effectivePrices(List.of(item(1, null, "20.00"), item(2, null, "50.00")));

        assertThat(result).extracting(PriceResponse::productId).containsExactly(1L, 2L);
        assertThat(result.get(0).discountAmount()).isEqualByComparingTo("0");
        assertThat(result.get(1).effectivePrice()).isEqualByComparingTo("45.00");
        assertThat(result.get(1).campaignId()).isEqualTo(3L);
    }

    @Test
    void sendsDistinctProductIdsAndAPlaceholderWhenNoItemHasACategory() {
        when(targets.findActiveOffers(any(), any(Instant.class), any(), anyCollection(), any(), anyCollection()))
                .thenReturn(List.of());

        service.effectivePrices(List.of(item(1, null, "20.00"), item(1, null, "20.00")));

        verify(targets).findActiveOffers(any(), any(Instant.class), eq(TargetType.PRODUCT), productIds.capture(),
                eq(TargetType.CATEGORY), categoryIds.capture());
        assertThat(productIds.getValue()).containsExactly(1L);
        assertThat(categoryIds.getValue()).containsExactly(-1L);
    }
}
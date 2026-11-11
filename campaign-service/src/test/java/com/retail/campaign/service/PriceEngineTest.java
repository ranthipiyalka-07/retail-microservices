package com.retail.campaign.service;

import com.retail.campaign.domain.DiscountType;
import com.retail.campaign.domain.TargetType;
import com.retail.campaign.dto.PriceQueryItem;
import com.retail.campaign.dto.PriceResponse;
import com.retail.campaign.repository.ActiveOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceEngineTest {

    private final PriceEngine engine = new PriceEngine();

    private static PriceQueryItem item(long productId, Long categoryId, String price) {
        return new PriceQueryItem(productId, categoryId, new BigDecimal(price));
    }

    private static ActiveOffer percent(long campaignId, String value, TargetType type, long targetId) {
        return new ActiveOffer(campaignId, "Campaign " + campaignId, DiscountType.PERCENTAGE, new BigDecimal(value), type, targetId);
    }

    private static ActiveOffer fixed(long campaignId, String value, TargetType type, long targetId) {
        return new ActiveOffer(campaignId, "Campaign " + campaignId, DiscountType.FIXED_AMOUNT, new BigDecimal(value), type, targetId);
    }

    @Test
    void percentageDiscountIsRoundedHalfUp() {
        PriceResponse price = engine.price(item(1, null, "19.99"), List.of(percent(5, "20", TargetType.PRODUCT, 1)));

        assertThat(price.discountAmount()).isEqualByComparingTo("4.00");        // 3.998 rounded
        assertThat(price.effectivePrice()).isEqualByComparingTo("15.99");
        assertThat(price.basePrice()).isEqualByComparingTo("19.99");
        assertThat(price.campaignId()).isEqualTo(5L);
        assertThat(price.campaignName()).isEqualTo("Campaign 5");
    }

    @Test
    void fixedAmountIsSubtractedFromThePrice() {
        PriceResponse price = engine.price(item(1, null, "19.99"), List.of(fixed(5, "5", TargetType.PRODUCT, 1)));

        assertThat(price.effectivePrice()).isEqualByComparingTo("14.99");
        assertThat(price.discountAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void anItemWithoutAMatchingOfferKeepsItsPrice() {
        PriceResponse price = engine.price(item(1, 9L, "19.99"), List.of(percent(5, "20", TargetType.PRODUCT, 2)));

        assertThat(price.effectivePrice()).isEqualByComparingTo("19.99");
        assertThat(price.discountAmount()).isEqualByComparingTo("0");
        assertThat(price.campaignId()).isNull();
        assertThat(price.campaignName()).isNull();
    }

    @Test
    void theBiggestDiscountInMoneyWins() {
        List<ActiveOffer> offers = List.of(percent(1, "20", TargetType.PRODUCT, 1), fixed(2, "5", TargetType.CATEGORY, 9));

        PriceResponse price = engine.price(item(1, 9L, "19.99"), offers);

        assertThat(price.campaignId()).isEqualTo(2L);                           // 5.00 beats 4.00
        assertThat(price.effectivePrice()).isEqualByComparingTo("14.99");
    }

    @Test
    void aTieGoesToTheOlderCampaignWhateverTheOrder() {
        ActiveOffer older = fixed(3, "5", TargetType.PRODUCT, 1);
        ActiveOffer newer = fixed(7, "5", TargetType.CATEGORY, 9);

        assertThat(engine.price(item(1, 9L, "19.99"), List.of(older, newer)).campaignId()).isEqualTo(3L);
        assertThat(engine.price(item(1, 9L, "19.99"), List.of(newer, older)).campaignId()).isEqualTo(3L);
    }

    @Test
    void categoryTargetsOnlyMatchWhenTheCallerSendsTheCategory() {
        List<ActiveOffer> offers = List.of(fixed(1, "5", TargetType.CATEGORY, 9));

        assertThat(engine.price(item(1, 9L, "19.99"), offers).campaignId()).isEqualTo(1L);
        assertThat(engine.price(item(1, null, "19.99"), offers).campaignId()).isNull();
        assertThat(engine.price(item(1, 8L, "19.99"), offers).campaignId()).isNull();
    }

    @Test
    void aBigFixedDiscountLeavesTheMinimumPrice() {
        PriceResponse price = engine.price(item(1, null, "3.00"), List.of(fixed(1, "10", TargetType.PRODUCT, 1)));

        assertThat(price.effectivePrice()).isEqualByComparingTo("0.01");
        assertThat(price.discountAmount()).isEqualByComparingTo("2.99");
    }

    @Test
    void aDiscountThatRoundsToNothingIsNoDiscount() {
        PriceResponse price = engine.price(item(1, null, "0.10"), List.of(percent(1, "1", TargetType.PRODUCT, 1)));

        assertThat(price.discountAmount()).isEqualByComparingTo("0");
        assertThat(price.campaignId()).isNull();                                // 0.001 rounds to 0.00
    }

    @Test
    void theCheapestPossibleItemIsNeverDiscounted() {
        PriceResponse price = engine.price(item(1, null, "0.01"), List.of(percent(1, "50", TargetType.PRODUCT, 1)));

        assertThat(price.effectivePrice()).isEqualByComparingTo("0.01");
        assertThat(price.campaignId()).isNull();
    }
}
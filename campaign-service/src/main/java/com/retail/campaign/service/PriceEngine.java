package com.retail.campaign.service;

import com.retail.campaign.domain.DiscountType;
import com.retail.campaign.domain.TargetType;
import com.retail.campaign.dto.PriceQueryItem;
import com.retail.campaign.dto.PriceResponse;
import com.retail.campaign.repository.ActiveOffer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceEngine {

    static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Picks the offer with the biggest discount in money (assumption A11). A tie goes to the older campaign,
     * so the result never depends on the order of the offers.
     */
    public PriceResponse price(PriceQueryItem item, List<ActiveOffer> offers) {
        BigDecimal base = item.basePrice().setScale(2, RoundingMode.HALF_UP);
        ActiveOffer best = null;
        BigDecimal bestDiscount = BigDecimal.ZERO;

        for (ActiveOffer offer : offers) {
            if (!matches(offer, item)) {
                continue;
            }
            BigDecimal discount = discount(offer, base);
            int cmp = discount.compareTo(bestDiscount);
            if (discount.signum() > 0 && (cmp > 0 || (cmp == 0 && offer.campaignId() < best.campaignId()))) {
                best = offer;
                bestDiscount = discount;
            }
        }

        if (best == null) {
            return new PriceResponse(item.productId(), base, base, BigDecimal.ZERO.setScale(2), null, null);
        }
        return new PriceResponse(item.productId(), base, base.subtract(bestDiscount), bestDiscount,
                best.campaignId(), best.campaignName());
    }

    private static boolean matches(ActiveOffer offer, PriceQueryItem item) {
        return (offer.targetType() == TargetType.PRODUCT && offer.targetId().equals(item.productId()))
                || (offer.targetType() == TargetType.CATEGORY && item.categoryId() != null
                && offer.targetId().equals(item.categoryId()));
    }

    /** Never takes the price below 0.01, and never gives a discount that rounds to nothing. */
    private static BigDecimal discount(ActiveOffer offer, BigDecimal base) {
        BigDecimal raw = offer.discountType() == DiscountType.PERCENTAGE
                ? base.multiply(offer.discountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : offer.discountValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = base.subtract(MIN_PRICE).max(BigDecimal.ZERO);
        return raw.min(max);
    }
}
package com.retail.order.service;

import com.retail.order.config.OrderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceCalculator {

    public record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal shipping, BigDecimal grandTotal) {}

    private final OrderProperties props;

    public PriceCalculator(OrderProperties props) {
        this.props = props;
    }

    /** Campaign discounts will be subtracted here once campaign-service exists. */
    public Totals compute(List<PricedLine> lines) {
        BigDecimal subtotal = lines.stream().map(PricedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(props.taxRate()).setScale(2, RoundingMode.HALF_UP);
        boolean freeShipping = props.freeShippingThreshold() != null
                && subtotal.compareTo(props.freeShippingThreshold()) >= 0;
        BigDecimal shipping = freeShipping
                ? BigDecimal.ZERO.setScale(2) : props.shippingFee().setScale(2, RoundingMode.HALF_UP);
        return new Totals(subtotal, tax, shipping, subtotal.add(tax).add(shipping));
    }
}
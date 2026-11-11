package com.retail.order.service;

import com.retail.order.config.OrderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceCalculator {

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal shipping,
                         BigDecimal grandTotal) {}

    private final OrderProperties props;

    public PriceCalculator(OrderProperties props) {
        this.props = props;
    }

    public Totals compute(List<PricedLine> lines) {
        BigDecimal gross = lines.stream().map(PricedLine::grossTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = lines.stream().map(PricedLine::lineDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(discount);

        BigDecimal tax = net.multiply(props.taxRate()).setScale(2, RoundingMode.HALF_UP);
        boolean freeShipping = props.freeShippingThreshold() != null
                && net.compareTo(props.freeShippingThreshold()) >= 0;
        BigDecimal shipping = freeShipping
                ? BigDecimal.ZERO.setScale(2) : props.shippingFee().setScale(2, RoundingMode.HALF_UP);
        return new Totals(gross, discount, tax, shipping, net.add(tax).add(shipping));
    }
}
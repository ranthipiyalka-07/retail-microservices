package com.retail.order.service;

import com.retail.order.config.OrderProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator(new OrderProperties(
            "USD", new BigDecimal("0.10"), new BigDecimal("5.00"), new BigDecimal("100.00"),
            Duration.ofMinutes(2), Duration.ofMinutes(10), 30));

    private static PricedLine line(String price, int quantity) {
        return new PricedLine(1L, "SKU-1", "Item", new BigDecimal(price), quantity);
    }

    @Test
    void addsTaxAndShippingBelowTheFreeShippingThreshold() {
        var totals = calculator.compute(List.of(line("19.99", 2)));

        assertThat(totals.subtotal()).isEqualByComparingTo("39.98");
        assertThat(totals.tax()).isEqualByComparingTo("4.00");           // 3.998 rounded half up
        assertThat(totals.shipping()).isEqualByComparingTo("5.00");
        assertThat(totals.grandTotal()).isEqualByComparingTo("48.98");
    }

    @Test
    void shippingIsFreeAtTheThreshold() {
        var totals = calculator.compute(List.of(line("50.00", 2)));

        assertThat(totals.shipping()).isEqualByComparingTo("0.00");
        assertThat(totals.grandTotal()).isEqualByComparingTo("110.00");
    }

    @Test
    void addsUpSeveralLinesAndRoundsTaxHalfUp() {
        var totals = calculator.compute(List.of(line("19.99", 2), line("24.50", 1)));

        assertThat(totals.subtotal()).isEqualByComparingTo("64.48");
        assertThat(totals.tax()).isEqualByComparingTo("6.45");           // 6.448 rounded half up
        assertThat(totals.grandTotal()).isEqualByComparingTo("75.93");
    }

    @Test
    void discountsLowerTheTaxAndCanMakeShippingFree() {
        var totals = calculator.compute(List.of(
                new PricedLine(1L, "SKU-1", "Item", new BigDecimal("60.00"), new BigDecimal("10.00"), 2)));

        assertThat(totals.subtotal()).isEqualByComparingTo("120.00");           // before the discount
        assertThat(totals.discount()).isEqualByComparingTo("20.00");
        assertThat(totals.tax()).isEqualByComparingTo("10.00");                 // 10% of 100.00
        assertThat(totals.shipping()).isEqualByComparingTo("0.00");             // 100.00 reaches the free-shipping threshold
        assertThat(totals.grandTotal()).isEqualByComparingTo("110.00");
    }
}
package com.retail.order.service;

import java.math.BigDecimal;

/** unitPrice is the base (list) price. discountPerUnit is what the campaign takes off one unit. */
public record PricedLine(Long productId, String sku, String name, BigDecimal unitPrice,
                         BigDecimal discountPerUnit, int quantity) {

    /** A line without any discount. */
    public PricedLine(Long productId, String sku, String name, BigDecimal unitPrice, int quantity) {
        this(productId, sku, name, unitPrice, BigDecimal.ZERO.setScale(2), quantity);
    }

    public BigDecimal grossTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal lineDiscount() {
        return discountPerUnit.multiply(BigDecimal.valueOf(quantity));
    }

    /** What the customer pays for the line. */
    public BigDecimal lineTotal() {
        return grossTotal().subtract(lineDiscount());
    }
}
package com.retail.order.service;

import java.math.BigDecimal;

public record PricedLine(Long productId, String sku, String name, BigDecimal unitPrice, int quantity) {
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
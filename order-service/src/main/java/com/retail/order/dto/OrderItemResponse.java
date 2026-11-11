package com.retail.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, String sku, String name, BigDecimal unitPrice,
                                BigDecimal discountAmount, int quantity, BigDecimal lineTotal) {}
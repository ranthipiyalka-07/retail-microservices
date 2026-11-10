package com.retail.order.dto;

import com.retail.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummary(Long id, String orderNumber, Long customerId, OrderStatus status,
                           BigDecimal grandTotal, String currency, Instant placedAt, int itemCount) {}
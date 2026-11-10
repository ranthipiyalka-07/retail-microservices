package com.retail.order.service;

import com.retail.order.dto.AddressInput;

import java.util.List;

/** Everything needed to store a new PENDING order. */
public record NewOrder(Long customerId, String customerEmail, Long cartId, String idempotencyKey, String currency,
                       List<PricedLine> lines, PriceCalculator.Totals totals,
                       AddressInput shipping, AddressInput billing) {}
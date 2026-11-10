package com.retail.order.dto;

import com.retail.order.domain.OrderStatus;

public record PurchaseResponse(Long orderId, OrderStatus status) {}
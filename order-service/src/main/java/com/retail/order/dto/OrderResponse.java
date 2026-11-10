package com.retail.order.dto;

import com.retail.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String orderNumber, OrderStatus status,
                            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
                            BigDecimal shippingFee, BigDecimal grandTotal, String currency,
                            String failureReason, Instant placedAt,
                            List<OrderItemResponse> items, AddressResponse shippingAddress,
                            AddressResponse billingAddress, ShipmentResponse shipment, List<StatusEntry> timeline) {}
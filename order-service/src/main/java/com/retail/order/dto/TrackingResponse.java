package com.retail.order.dto;

import com.retail.order.domain.OrderStatus;

import java.util.List;

public record TrackingResponse(String orderNumber, OrderStatus status, ShipmentResponse shipment,
                               List<StatusEntry> timeline) {}
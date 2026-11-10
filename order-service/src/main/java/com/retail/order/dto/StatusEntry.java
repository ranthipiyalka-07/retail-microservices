package com.retail.order.dto;

import com.retail.order.domain.OrderStatus;

import java.time.Instant;

/** One step of the order timeline. Who made the change is not shown to customers. */
public record StatusEntry(OrderStatus from, OrderStatus to, String comment, Instant at) {}
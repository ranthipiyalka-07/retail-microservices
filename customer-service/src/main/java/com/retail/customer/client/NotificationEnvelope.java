package com.retail.customer.client;

/** type is the notification-service event type, for example CUSTOMER_REGISTERED. */
public record NotificationEnvelope(String type, Object payload) {}
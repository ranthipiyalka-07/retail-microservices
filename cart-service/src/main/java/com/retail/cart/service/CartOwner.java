package com.retail.cart.service;

/** Who is calling: a customer (customerId) or a guest (token, possibly null for a first-time visitor). */
public record CartOwner(Long customerId, String guestToken) {

    public static CartOwner customer(Long customerId) { return new CartOwner(customerId, null); }

    public static CartOwner guest(String guestToken) { return new CartOwner(null, guestToken); }

    public boolean isCustomer() { return customerId != null; }
}
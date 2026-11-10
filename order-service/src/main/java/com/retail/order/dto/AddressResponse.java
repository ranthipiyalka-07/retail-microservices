package com.retail.order.dto;

public record AddressResponse(String name, String line1, String line2, String city, String state,
                              String postalCode, String country, String phone) {}
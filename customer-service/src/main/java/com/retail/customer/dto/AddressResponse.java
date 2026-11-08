package com.retail.customer.dto;

import com.retail.customer.domain.AddressType;

public record AddressResponse(Long id, AddressType type, String line1, String line2, String city,
                              String state, String postalCode, String country, boolean defaultAddress) {}
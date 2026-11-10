package com.retail.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressInput(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter country code") String country,
        @Pattern(regexp = "^[+0-9 ()\\-]{7,30}$", message = "must be a valid phone number") String phone) {}
package com.retail.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,       // 72 = BCrypt input limit
        @Pattern(regexp = "^[+0-9 ()\\-]{7,30}$", message = "must be a valid phone number") String phone,
        Boolean consentMarketing) {

    @Override
    public String toString() {                                    // never print the password
        return "RegisterRequest[***]";
    }
}
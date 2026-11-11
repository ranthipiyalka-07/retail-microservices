package com.retail.wishlist.web;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class CurrentCustomer {
    private CurrentCustomer() {}

    /** The customerId claim exists only on customer accounts: staff accounts have no wishlist. */
    static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaim("customerId") instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("Not a customer account");
    }
}
package com.retail.customer.web;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class CurrentCustomer {
    private CurrentCustomer() {}

    /** The customerId claim exists only on customer accounts (staff accounts have none). */
    static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Object claim = jwt.getClaim("customerId");
            if (claim instanceof Number number) {
                return number.longValue();
            }
            if (claim instanceof String text && !text.isBlank()) {
                try {
                    return Long.valueOf(text.trim());
                } catch (NumberFormatException ignored) {
                    // falls through to AccessDenied below
                }
            }
        }
        throw new AccessDenied();
    }

    private static class AccessDenied extends AccessDeniedException {
        AccessDenied() { super("Not a customer account"); }
    }
}
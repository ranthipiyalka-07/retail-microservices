package com.retail.order.web;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class CurrentCustomer {
    private CurrentCustomer() {}

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("Not authenticated");
    }

    /** The customerId claim exists only on customer accounts. */
    static Long id() {
        Object claim = jwt().getClaim("customerId");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("Not a customer account");
    }

    /** The token's subject is the login email. */
    static String email() {
        return jwt().getSubject();
    }
}
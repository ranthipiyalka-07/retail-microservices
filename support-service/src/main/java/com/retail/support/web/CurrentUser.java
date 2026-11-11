package com.retail.support.web;

import com.retail.support.service.Staff;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class CurrentUser {
    private CurrentUser() {}

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("Not authenticated");
    }

    /** The customerId claim exists only on customer accounts. */
    static Long customerId() {
        if (jwt().getClaim("customerId") instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("Not a customer account");
    }

    /** The token's subject is the login email. */
    static String email() {
        return jwt().getSubject();
    }

    static Staff staff() {
        Jwt jwt = jwt();
        if (jwt.getClaim("userId") instanceof Number number) {
            return new Staff(number.longValue(), jwt.getSubject());
        }
        throw new AccessDeniedException("Not a staff account");
    }
}
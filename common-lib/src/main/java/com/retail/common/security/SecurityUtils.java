package com.retail.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public final class SecurityUtils {

    private SecurityUtils(){

    }

    private static Jwt jwt(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.getPrincipal() instanceof Jwt jwt) return jwt;
        throw new IllegalStateException("No Authenticated JWT in Security Context");
    }

    public static Long currentCustomerId(){
        Number id = jwt().getClaim("customerId");
        return id==null?null:id.longValue();
    }

    public static List<String> currentRoles(){
        List<String> roles = jwt().getClaimAsStringList("roles");
        return roles==null?List.of():roles;
    }

    public static boolean isOwner(Long resourceCustomerId){
       return resourceCustomerId!= null&&resourceCustomerId.equals(currentCustomerId());
    }

}

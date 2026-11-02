package com.retail.auth.config;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix="retail.auth")
public record AuthProperties(@DefaultValue("5") int maxFailedAttempts,
                             @DefaultValue("15m") Duration lockDuration,
                             BootstrapAdmin bootstrapAdmin) {
     public record BootstrapAdmin(String email,String password){}
}

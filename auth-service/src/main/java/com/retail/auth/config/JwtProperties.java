package com.retail.auth.config;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "retail.jwt")
public record JwtProperties(@DefaultValue("retail-auth") String issuer,
                            @DefaultValue("15m") Duration accessTokenTtl,
                            @DefaultValue("7d") Duration refreshTokenTtl,
                            String privateKey,
                            String publicKey) {
}

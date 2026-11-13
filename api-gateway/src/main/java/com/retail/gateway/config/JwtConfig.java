package com.retail.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${jwt.public-key:#{null}}")
    private String jwtPublicKey;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
        // Try to get from environment variable if not in properties
        String publicKey = jwtPublicKey;
        if ((publicKey == null || publicKey.isEmpty()) && System.getenv("JWT_PUBLIC_KEY") != null) {
            publicKey = System.getenv("JWT_PUBLIC_KEY");
        }
        
        if (publicKey != null && !publicKey.isEmpty()) {
            try {
                // Decode the public key from Base64 (X.509 format)
                byte[] decodedKey = Base64.getDecoder().decode(publicKey);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey key = kf.generatePublic(spec);
                return NimbusReactiveJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) key).build();
            } catch (Exception e) {
                System.err.println("Failed to load JWT public key: " + e.getMessage());
            }
        }
        // Fallback: use HMAC if no public key provided (for testing only)
        return NimbusReactiveJwtDecoder.withSecretKey(
                new SecretKeySpec("fallback-secret-key-for-testing-only-do-not-use-in-production".getBytes(), "HmacSHA256")
        ).build();
    }
}

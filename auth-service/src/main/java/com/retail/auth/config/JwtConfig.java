package com.retail.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RSAKey rsaKey(JwtProperties props) throws Exception {
        RSAPublicKey publicKey;
        java.security.PrivateKey privateKey;

        if (StringUtils.hasText(props.privateKey()) && StringUtils.hasText(props.publicKey())) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(props.privateKey())));
            publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(props.publicKey())));
            log.info("JWT signing key loaded from configuration");
        } else {
            log.warn("No JWT key configured: generating a temporary RSA key pair. "
                    + "All tokens become invalid when auth-service restarts. Configure retail.jwt.private-key/public-key outside dev.");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = pair.getPrivate();
        }

        RSAKey withoutId = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new RSAKey.Builder(withoutId).keyID(withoutId.computeThumbprint().toString()).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }
}
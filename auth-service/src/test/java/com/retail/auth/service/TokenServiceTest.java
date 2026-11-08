package com.retail.auth.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.retail.auth.config.JwtProperties;
import com.retail.auth.domain.Role;
import com.retail.auth.domain.UserCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    @Test
    void accessTokenHasExpectedClaimsAndVerifiesWithPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();

        var props = new JwtProperties("retail-auth", Duration.ofMinutes(15), Duration.ofDays(7), null, null);
        var service = new TokenService(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey))), props);

        UserCredentials user = new UserCredentials();
        user.setId(7L);
        user.setCustomerId(42L);
        user.setEmail("john@example.com");
        user.getRoles().add(new Role("CUSTOMER"));

        TokenService.IssuedToken issued = service.createAccessToken(user);

        Jwt jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build().decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("john@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("retail-auth");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(((Number) jwt.getClaim("customerId")).longValue()).isEqualTo(42L);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void refreshTokensAreUniqueAndHashIsStable() {
        var service = new TokenService(null, new JwtProperties(null, null, null, null, null));
        String a = service.newRefreshTokenValue();
        String b = service.newRefreshTokenValue();

        assertThat(a).isNotEqualTo(b).hasSizeGreaterThan(40);
        assertThat(TokenService.hash(a)).isEqualTo(TokenService.hash(a)).hasSize(64).isNotEqualTo(a);
    }
}
package com.retail.auth.service;

import com.retail.auth.config.AuthProperties;
import com.retail.auth.config.JwtProperties;
import com.retail.auth.domain.AccountStatus;
import com.retail.auth.domain.RefreshToken;
import com.retail.auth.domain.UserCredentials;
import com.retail.auth.dto.LoginRequest;
import com.retail.auth.dto.TokenResponse;
import com.retail.auth.exception.AccountLockedException;
import com.retail.auth.exception.InvalidCredentialsException;
import com.retail.auth.exception.InvalidTokenException;
import com.retail.auth.repository.RefreshTokenRepository;
import com.retail.auth.repository.RoleRepository;
import com.retail.auth.repository.UserCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserCredentialsRepository users;
    @Mock RoleRepository roles;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock TokenService tokenService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);   // low cost: fast tests
    private AuthService service;
    private UserCredentials user;

    @BeforeEach
    void setUp() {
        var authProps = new AuthProperties(3, Duration.ofMinutes(15), null);
        var jwtProps = new JwtProperties("test", Duration.ofMinutes(15), Duration.ofDays(7), null, null);
        service = new AuthService(users, roles, refreshTokens, encoder, tokenService, authProps, jwtProps);

        user = new UserCredentials();
        user.setId(5L);
        user.setEmail("john@example.com");
        user.setPasswordHash(encoder.encode("Password1"));
        user.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    void successfulLoginReturnsTokensAndResetsFailureCounter() {
        user.setFailedAttempts(2);
        when(users.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(tokenService.createAccessToken(user)).thenReturn(new TokenService.IssuedToken("access", 900));
        when(tokenService.newRefreshTokenValue()).thenReturn("refresh");

        TokenResponse response = service.login(new LoginRequest("John@Example.com", "Password1"), "junit");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    void wrongPasswordIsRejectedAndCounted() {
        when(users.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("john@example.com", "wrong"), null))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void unknownEmailGivesTheSameErrorAsWrongPassword() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "x"), null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void accountLocksAfterMaxFailuresEvenForTheCorrectPassword() {
        when(users.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("john@example.com", "wrong"), null))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThat(user.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThatThrownBy(() -> service.login(new LoginRequest("john@example.com", "Password1"), null))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void reusedRefreshTokenRevokesAllSessions() {
        var revoked = new RefreshToken(user, TokenService.hash("old"), Instant.now().plusSeconds(3600), null);
        revoked.setRevoked(true);
        when(refreshTokens.findByTokenHash(TokenService.hash("old"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("old", null)).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokens).revokeAllForUser(5L);
    }
}
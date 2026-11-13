package com.retail.auth.service;

import com.retail.auth.config.AuthProperties;
import com.retail.auth.config.JwtProperties;
import com.retail.auth.domain.AccountStatus;
import com.retail.auth.domain.RefreshToken;
import com.retail.auth.domain.Role;
import com.retail.auth.domain.UserCredentials;
import com.retail.auth.dto.CreateCredentialsRequest;
import com.retail.auth.dto.CredentialsResponse;
import com.retail.auth.dto.LoginRequest;
import com.retail.auth.dto.TokenResponse;
import com.retail.auth.exception.AccountLockedException;
import com.retail.auth.exception.CredentialsAlreadyExistException;
import com.retail.auth.exception.InvalidCredentialsException;
import com.retail.auth.exception.InvalidTokenException;
import com.retail.auth.repository.RefreshTokenRepository;
import com.retail.auth.repository.RoleRepository;
import com.retail.auth.repository.UserCredentialsRepository;
import com.retail.common.audit.Audit;
import com.retail.common.security.Roles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserCredentialsRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthProperties authProps;
    private final JwtProperties jwtProps;
    private final String dummyHash;

    public AuthService(UserCredentialsRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, TokenService tokenService, AuthProperties authProps,
                       JwtProperties jwtProps) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authProps = authProps;
        this.jwtProps = jwtProps;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * noRollbackFor: the failed-attempt counter and lock must be saved even though we throw.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public TokenResponse login(LoginRequest request,String deviceInfo){
        Instant now=Instant.now();
        UserCredentials user = users.findByEmail(normalize(request.email())).orElse(null);

        if(user ==null){
            Audit.recordAs(Audit.Actor.user(normalize(request.email())), "auth.login", Audit.Outcome.FAILURE, "user", null, "unknown email");
            passwordEncoder.matches(request.password(),dummyHash);
            throw new InvalidCredentialsException();
        }

        if(user.getStatus()== AccountStatus.LOCKED){
            if(user.getLockedUntil()!=null && user.getLockedUntil().isAfter(now)){
                Audit.recordAs(Audit.Actor.user(user.getEmail()), "auth.login", Audit.Outcome.FAILURE, "user", user.getId(), "account locked");
                throw new AccountLockedException();
            }
            user.unlock();
        }

        if(user.getStatus()!=AccountStatus.ACTIVE){
            throw new InvalidCredentialsException();
        }

        if(!passwordEncoder.matches(request.password(),user.getPasswordHash())){
            user.registerFailedLogin(authProps.maxFailedAttempts(),authProps.lockDuration(),now);
            log.warn("Failed login for userId={} (failed attempts: {})", user.getId(), user.getFailedAttempts());
            throw new InvalidCredentialsException();
        }

        user.registerSuccessfulLogin(now);
        Audit.recordAs(Audit.Actor.user(user.getEmail()), "auth.login", Audit.Outcome.SUCCESS, "user", user.getId(), null);
        return issueTokens(user,deviceInfo);
    }

    /** Rotating refresh tokens: each token works once. A reused (revoked) token revokes all sessions of the user. */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String refreshTokenValue,String deviceInfo){
        RefreshToken stored = refreshTokens.findByTokenHash(TokenService.hash(refreshTokenValue)).orElseThrow(InvalidTokenException::new);
        if(stored.isRevoked()){
            refreshTokens.revokeAllForUser(stored.getUser().getId());
            log.warn("Refresh token reuse detected for userId={}: all sessions revoked", stored.getUser().getId());
            Audit.recordAs(Audit.Actor.user(stored.getUser().getEmail()), "auth.refresh.reuse", Audit.Outcome.FAILURE, "user", stored.getUser().getId(), "refresh token reuse: all sessions revoked");
            throw new InvalidTokenException();
        }

        if(stored.getExpiresAt().isBefore(Instant.now())){
            throw new InvalidTokenException();
        }

        UserCredentials user = stored.getUser();
        if(user.getStatus()!=AccountStatus.ACTIVE){
            throw new InvalidTokenException();
        }
        stored.setRevoked(true);
        return issueTokens(user,deviceInfo);
    }

    /** Idempotent: unknown tokens are ignored. */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokens.findByTokenHash(TokenService.hash(refreshTokenValue))
                .ifPresent(token -> token.setRevoked(true));
    }

    /** Called by customer-service during registration. */
    @Transactional
    public CredentialsResponse createCredentials(CreateCredentialsRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new CredentialsAlreadyExistException("An account with this email already exists");
        }
        if (users.existsByCustomerId(request.customerId())) {
            throw new CredentialsAlreadyExistException("Credentials already exist for this customer");
        }

        Role customerRole = roles.findByName(Roles.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER is missing: check the Flyway migration"));

        UserCredentials user = new UserCredentials();
        user.setCustomerId(request.customerId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(AccountStatus.ACTIVE);
        user.getRoles().add(customerRole);

        try {
            users.saveAndFlush(user);       // flush now so a duplicate shows up here, not at commit
            Audit.record("auth.credentials.create", Audit.Outcome.SUCCESS, "user", user.getId(), null);
        } catch (DataIntegrityViolationException e) {
            throw new CredentialsAlreadyExistException("An account with this email already exists");
        }
        return new CredentialsResponse(user.getId(), user.getCustomerId(), user.getEmail());
    }


    private static String normalize(String email){
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private TokenResponse issueTokens(UserCredentials user,String deviceInfo){
        TokenService.IssuedToken access= tokenService.createAccessToken(user);
        String refreshValue= tokenService.newRefreshTokenValue();
        refreshTokens.save(new RefreshToken(user,TokenService.hash(refreshValue),Instant.now().plus(jwtProps.refreshTokenTtl()),
                truncate(deviceInfo)));
        return new TokenResponse(access.value(), refreshValue,"Bearer", access.expiresInSeconds());
    }

    private static String truncate(String value){
        return value == null? null:value.substring(0,Math.min(value.length(),255));
    }

}

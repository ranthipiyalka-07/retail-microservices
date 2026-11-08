package com.retail.auth.web;

import com.retail.auth.dto.LoginRequest;
import com.retail.auth.dto.RefreshRequest;
import com.retail.auth.dto.TokenResponse;
import com.retail.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               @RequestHeader(value = "User-Agent",required = false) String userAgent) {
        return noStore(authService.login(request, userAgent));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                 @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return noStore(authService.refresh(request.refreshToken(), userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<TokenResponse> noStore(TokenResponse body){
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(body);
    }
}

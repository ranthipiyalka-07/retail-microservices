package com.retail.auth.web;

import com.retail.auth.dto.CreateCredentialsRequest;
import com.retail.auth.dto.CredentialsResponse;
import com.retail.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/auth")
public class InternalCredentialsController {

    private final AuthService authService;

    public InternalCredentialsController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialsResponse create(@Valid @RequestBody CreateCredentialsRequest request) {
        return authService.createCredentials(request);
    }
}
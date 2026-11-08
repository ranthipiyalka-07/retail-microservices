package com.retail.auth.web;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;

@RestController
public class JwksController {

    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    /** Public keys only: the private key never leaves this service. */
    @GetMapping(value = "/api/v1/auth/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> jwks(){
        return new JWKSet(rsaKey).toPublicJWKSet().toJSONObject();
    }
}

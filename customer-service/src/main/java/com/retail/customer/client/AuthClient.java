package com.retail.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service")          // resolved through Eureka
public interface AuthClient {

    @PostMapping("/internal/auth/credentials")
    CredentialsResponse createCredentials(@RequestHeader("X-Internal-Key") String internalKey,
                                          @RequestBody CreateCredentialsRequest request);
}
package com.retail.auth.dto;

public record CredentialsResponse(Long userId, Long customerId, String email) {}
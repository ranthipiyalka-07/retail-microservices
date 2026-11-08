package com.retail.customer.dto;

import com.retail.customer.domain.CustomerStatus;

import java.time.Instant;

public record CustomerResponse(Long id, String firstName, String lastName, String email, String phone,
                               CustomerStatus status, boolean consentMarketing, Instant createdAt) {}
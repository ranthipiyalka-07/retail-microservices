package com.retail.customer.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.CustomerRegisteredEvent;
import com.retail.customer.client.AuthClient;
import com.retail.customer.client.CreateCredentialsRequest;
import com.retail.customer.domain.Customer;
import com.retail.customer.domain.CustomerStatus;
import com.retail.customer.dto.CustomerResponse;
import com.retail.customer.dto.RegisterRequest;
import com.retail.customer.dto.UpdateProfileRequest;
import com.retail.customer.exception.AuthServiceUnavailableException;
import com.retail.customer.exception.EmailAlreadyRegisteredException;
import com.retail.customer.repository.CustomerRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customers;
    private final AuthClient authClient;
    private final ApplicationEventPublisher events;
    private final String internalKey;

    public CustomerService(CustomerRepository customers, AuthClient authClient, ApplicationEventPublisher events,
                           @Value("${retail.internal.api-key}") String internalKey) {
        this.customers = customers;
        this.authClient = authClient;
        this.events = events;
        this.internalKey = internalKey;
    }

    /**
     * The auth call happens inside the transaction on purpose: if auth-service rejects or is down,
     * the customer row is rolled back, so no customer exists without a login.
     */
    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (customers.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        Customer customer = new Customer();
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(email);
        customer.setPhone(blankToNull(request.phone()));
        customer.setStatus(CustomerStatus.ACTIVE);
        boolean consent = Boolean.TRUE.equals(request.consentMarketing());
        customer.setConsentMarketing(consent);
        if (consent) {
            customer.setConsentAt(Instant.now());
        }

        try {
            customers.saveAndFlush(customer);      // flush now: a concurrent duplicate fails here
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }

        try {
            authClient.createCredentials(internalKey,
                    new CreateCredentialsRequest(customer.getId(), email, request.password()));
        } catch (FeignException e) {
            if (e.status() == 409) {
                throw new EmailAlreadyRegisteredException();
            }
            log.error("auth-service call failed during registration of customerId={}: status={}",
                    customer.getId(), e.status());
            throw new AuthServiceUnavailableException();
        }

        events.publishEvent(new CustomerRegisteredEvent(UUID.randomUUID(), Instant.now(),
                customer.getId(), customer.getEmail(), customer.getFirstName()));
        log.info("Customer registered: customerId={}", customer.getId());
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(Long customerId) {
        return toResponse(find(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, UpdateProfileRequest request) {
        Customer customer = find(customerId);
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setPhone(blankToNull(request.phone()));

        boolean consent = Boolean.TRUE.equals(request.consentMarketing());
        if (consent && !customer.isConsentMarketing()) {
            customer.setConsentAt(Instant.now());
        }
        customer.setConsentMarketing(consent);
        return toResponse(customer);
    }

    private Customer find(Long customerId) {
        return customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    private static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
                c.getStatus(), c.isConsentMarketing(), c.getCreatedAt());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
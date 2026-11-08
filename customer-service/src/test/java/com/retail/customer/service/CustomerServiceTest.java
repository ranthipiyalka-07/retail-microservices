package com.retail.customer.service;

import com.retail.common.event.CustomerRegisteredEvent;
import com.retail.customer.client.AuthClient;
import com.retail.customer.client.CreateCredentialsRequest;
import com.retail.customer.client.CredentialsResponse;
import com.retail.customer.domain.Customer;
import com.retail.customer.dto.CustomerResponse;
import com.retail.customer.dto.RegisterRequest;
import com.retail.customer.exception.AuthServiceUnavailableException;
import com.retail.customer.exception.EmailAlreadyRegisteredException;
import com.retail.customer.repository.CustomerRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customers;
    @Mock AuthClient authClient;
    @Mock ApplicationEventPublisher events;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customers, authClient, events, "test-key");
    }

    private static RegisterRequest request() {
        return new RegisterRequest("John", "Doe", "John@Example.com", "Password1", "+94771234567", true);
    }

    private static Request feignRequest() {
        return Request.create(Request.HttpMethod.POST, "http://auth-service/internal/auth/credentials",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    private void customerGetsId(long id) {
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
    }

    @Test
    void registersCustomerCreatesCredentialsAndPublishesEvent() {
        customerGetsId(10L);
        when(authClient.createCredentials(eq("test-key"), any()))
                .thenReturn(new CredentialsResponse(1L, 10L, "john@example.com"));

        CustomerResponse response = service.register(request());

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("john@example.com");            // trimmed and lower-cased
        assertThat(response.consentMarketing()).isTrue();

        ArgumentCaptor<CreateCredentialsRequest> captor = ArgumentCaptor.forClass(CreateCredentialsRequest.class);
        verify(authClient).createCredentials(eq("test-key"), captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo(10L);
        assertThat(captor.getValue().email()).isEqualTo("john@example.com");
        assertThat(captor.getValue().password()).isEqualTo("Password1");
        verify(events).publishEvent(any(CustomerRegisteredEvent.class));
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnyRemoteCall() {
        when(customers.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request())).isInstanceOf(EmailAlreadyRegisteredException.class);
        verifyNoInteractions(authClient, events);
    }

    @Test
    void emailAlreadyKnownToAuthServiceGivesConflictAndNoEvent() {
        customerGetsId(10L);
        when(authClient.createCredentials(eq("test-key"), any()))
                .thenThrow(new FeignException.Conflict("conflict", feignRequest(), null, Map.of()));

        assertThatThrownBy(() -> service.register(request())).isInstanceOf(EmailAlreadyRegisteredException.class);
        verifyNoInteractions(events);
    }

    @Test
    void authServiceDownGivesServiceUnavailableAndNoEvent() {
        customerGetsId(10L);
        when(authClient.createCredentials(eq("test-key"), any()))
                .thenThrow(new FeignException.ServiceUnavailable("down", feignRequest(), null, Map.of()));

        assertThatThrownBy(() -> service.register(request())).isInstanceOf(AuthServiceUnavailableException.class);
        verifyNoInteractions(events);
    }
}
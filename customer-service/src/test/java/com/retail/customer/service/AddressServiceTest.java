package com.retail.customer.service;

import com.retail.customer.domain.AddressType;
import com.retail.customer.domain.Customer;
import com.retail.customer.domain.CustomerAddress;
import com.retail.customer.dto.AddressRequest;
import com.retail.customer.dto.AddressResponse;
import com.retail.customer.repository.CustomerAddressRepository;
import com.retail.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock CustomerRepository customers;
    @Mock CustomerAddressRepository addresses;

    private AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(customers, addresses);
        Customer customer = new Customer();
        customer.setId(10L);
        when(customers.findById(10L)).thenReturn(Optional.of(customer));
        when(addresses.save(any(CustomerAddress.class))).thenAnswer(returnsFirstArg());
    }

    private static AddressRequest request(Boolean makeDefault) {
        return new AddressRequest(AddressType.SHIPPING, "12 Galle Road", null, "Bentota", null, "80500", "lk", makeDefault);
    }

    @Test
    void firstAddressOfATypeBecomesDefaultAndCountryIsUpperCased() {
        when(addresses.findByCustomerIdAndTypeOrderByIdAsc(10L, AddressType.SHIPPING)).thenReturn(List.of());

        AddressResponse response = service.add(10L, request(false));

        assertThat(response.defaultAddress()).isTrue();
        assertThat(response.country()).isEqualTo("LK");
    }

    @Test
    void newDefaultAddressDemotesTheExistingDefault() {
        CustomerAddress existing = new CustomerAddress();
        existing.setId(1L);
        existing.setType(AddressType.SHIPPING);
        existing.setDefaultAddress(true);
        when(addresses.findByCustomerIdAndTypeOrderByIdAsc(10L, AddressType.SHIPPING)).thenReturn(List.of(existing));

        AddressResponse response = service.add(10L, request(true));

        assertThat(response.defaultAddress()).isTrue();
        assertThat(existing.isDefaultAddress()).isFalse();
    }

    @Test
    void additionalAddressWithoutDefaultFlagKeepsTheExistingDefault() {
        CustomerAddress existing = new CustomerAddress();
        existing.setId(1L);
        existing.setType(AddressType.SHIPPING);
        existing.setDefaultAddress(true);
        when(addresses.findByCustomerIdAndTypeOrderByIdAsc(10L, AddressType.SHIPPING)).thenReturn(List.of(existing));

        AddressResponse response = service.add(10L, request(null));

        assertThat(response.defaultAddress()).isFalse();
        assertThat(existing.isDefaultAddress()).isTrue();
    }
}
package com.retail.customer.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.customer.domain.AddressType;
import com.retail.customer.domain.Customer;
import com.retail.customer.domain.CustomerAddress;
import com.retail.customer.dto.AddressRequest;
import com.retail.customer.dto.AddressResponse;
import com.retail.customer.dto.UpdateAddressRequest;
import com.retail.customer.repository.CustomerAddressRepository;
import com.retail.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AddressService {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;

    public AddressService(CustomerRepository customers, CustomerAddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long customerId) {
        return addresses.findByCustomerIdOrderByIdAsc(customerId).stream().map(AddressService::toResponse).toList();
    }

    /** The first address of a type becomes the default for that type automatically. */
    @Transactional
    public AddressResponse add(Long customerId, AddressRequest request) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        List<CustomerAddress> sameType = addresses.findByCustomerIdAndTypeOrderByIdAsc(customerId, request.type());

        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        address.setType(request.type());
        apply(address, request.line1(), request.line2(), request.city(), request.state(),
                request.postalCode(), request.country());

        boolean makeDefault = sameType.isEmpty() || Boolean.TRUE.equals(request.defaultAddress());
        if (makeDefault) {
            sameType.forEach(existing -> existing.setDefaultAddress(false));
        }
        address.setDefaultAddress(makeDefault);
        return toResponse(addresses.save(address));
    }

    /** Sending defaultAddress=true makes this the default. To demote it, make another address the default. */
    @Transactional
    public AddressResponse update(Long customerId, Long addressId, UpdateAddressRequest request) {
        CustomerAddress address = findOwned(customerId, addressId);
        apply(address, request.line1(), request.line2(), request.city(), request.state(),
                request.postalCode(), request.country());

        if (Boolean.TRUE.equals(request.defaultAddress()) && !address.isDefaultAddress()) {
            addresses.findByCustomerIdAndTypeOrderByIdAsc(customerId, address.getType())
                    .forEach(other -> other.setDefaultAddress(other.getId().equals(address.getId())));
        }
        return toResponse(address);
    }

    /** If the deleted address was the default, the oldest remaining one of the same type takes over. */
    @Transactional
    public void delete(Long customerId, Long addressId) {
        CustomerAddress address = findOwned(customerId, addressId);
        AddressType type = address.getType();
        boolean wasDefault = address.isDefaultAddress();

        addresses.delete(address);
        addresses.flush();

        if (wasDefault) {
            addresses.findByCustomerIdAndTypeOrderByIdAsc(customerId, type)
                    .stream().findFirst().ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private CustomerAddress findOwned(Long customerId, Long addressId) {
        return addresses.findByIdAndCustomerId(addressId, customerId)     // another customer's id gives a 404
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    private static void apply(CustomerAddress a, String line1, String line2, String city, String state,
                              String postalCode, String country) {
        a.setLine1(line1.trim());
        a.setLine2(line2 == null || line2.isBlank() ? null : line2.trim());
        a.setCity(city.trim());
        a.setState(state == null || state.isBlank() ? null : state.trim());
        a.setPostalCode(postalCode.trim());
        a.setCountry(country.trim().toUpperCase(Locale.ROOT));
    }

    private static AddressResponse toResponse(CustomerAddress a) {
        return new AddressResponse(a.getId(), a.getType(), a.getLine1(), a.getLine2(), a.getCity(),
                a.getState(), a.getPostalCode(), a.getCountry(), a.isDefaultAddress());
    }
}
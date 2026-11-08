package com.retail.customer.repository;

import com.retail.customer.domain.AddressType;
import com.retail.customer.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomerIdOrderByIdAsc(Long customerId);
    List<CustomerAddress> findByCustomerIdAndTypeOrderByIdAsc(Long customerId, AddressType type);
    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);
}
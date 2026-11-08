package com.retail.customer.web;

import com.retail.customer.dto.AddressRequest;
import com.retail.customer.dto.AddressResponse;
import com.retail.customer.dto.UpdateAddressRequest;
import com.retail.customer.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list() {
        return addressService.list(CurrentCustomer.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse add(@Valid @RequestBody AddressRequest request) {
        return addressService.add(CurrentCustomer.id(), request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@PathVariable Long addressId, @Valid @RequestBody UpdateAddressRequest request) {
        return addressService.update(CurrentCustomer.id(), addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long addressId) {
        addressService.delete(CurrentCustomer.id(), addressId);
    }
}
package com.retail.customer.web;

import com.retail.customer.dto.CustomerResponse;
import com.retail.customer.dto.RegisterRequest;
import com.retail.customer.dto.UpdateProfileRequest;
import com.retail.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterRequest request) {
        return customerService.register(request);
    }

    @GetMapping("/me")
    public CustomerResponse me() {
        return customerService.getProfile(CurrentCustomer.id());
    }

    @PutMapping("/me")
    public CustomerResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return customerService.updateProfile(CurrentCustomer.id(), request);
    }
}
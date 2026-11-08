package com.retail.customer.client;

public record CreateCredentialsRequest(Long customerId, String email, String password) {
    @Override
    public String toString() {                 // never print the password
        return "CreateCredentialsRequest[customerId=" + customerId + "]";
    }
}
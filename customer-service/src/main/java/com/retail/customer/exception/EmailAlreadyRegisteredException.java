package com.retail.customer.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends RetailException {
    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
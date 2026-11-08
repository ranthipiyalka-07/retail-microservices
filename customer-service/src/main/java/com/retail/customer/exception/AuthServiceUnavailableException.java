package com.retail.customer.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class AuthServiceUnavailableException extends RetailException {
    public AuthServiceUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Registration is temporarily unavailable. Please try again later.");
    }
}
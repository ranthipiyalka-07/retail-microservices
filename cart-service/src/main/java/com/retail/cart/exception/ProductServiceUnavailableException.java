package com.retail.cart.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class ProductServiceUnavailableException extends RetailException {
    public ProductServiceUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Product information is temporarily unavailable. Please try again later.");
    }
}
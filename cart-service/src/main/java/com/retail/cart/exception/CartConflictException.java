package com.retail.cart.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class CartConflictException extends RetailException {
    public CartConflictException() {
        super(HttpStatus.CONFLICT, "The cart was changed at the same time by another request. Please try again.");
    }
}
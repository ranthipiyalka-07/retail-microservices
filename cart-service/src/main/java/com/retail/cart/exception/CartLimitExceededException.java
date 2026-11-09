package com.retail.cart.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class CartLimitExceededException extends RetailException {
    public CartLimitExceededException(String message) { super(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
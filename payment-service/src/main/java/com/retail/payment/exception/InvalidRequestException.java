package com.retail.payment.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class InvalidRequestException extends RetailException {
    public InvalidRequestException(String message) { super(HttpStatus.BAD_REQUEST, message); }
}
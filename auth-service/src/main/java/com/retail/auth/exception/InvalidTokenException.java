package com.retail.auth.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends RetailException {
    public InvalidTokenException() { super(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"); }
}
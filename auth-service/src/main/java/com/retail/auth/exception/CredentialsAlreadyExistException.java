package com.retail.auth.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class CredentialsAlreadyExistException extends RetailException {
    public CredentialsAlreadyExistException(String message) { super(HttpStatus.CONFLICT, message); }
}
package com.retail.support.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class LimitExceededException extends RetailException {
    public LimitExceededException(String message) { super(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
package com.retail.campaign.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class ConflictException extends RetailException {
    public ConflictException(String message) { super(HttpStatus.CONFLICT, message); }
}
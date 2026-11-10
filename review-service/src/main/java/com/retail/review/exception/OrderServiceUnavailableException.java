package com.retail.review.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class OrderServiceUnavailableException extends RetailException {
    public OrderServiceUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Your purchases cannot be checked right now. Please try again later.");
    }
}
package com.retail.wishlist.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class RemoteUnavailableException extends RetailException {
    public RemoteUnavailableException(String dependency) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "A required service (" + dependency + ") is temporarily unavailable. Please try again later.");
    }
}
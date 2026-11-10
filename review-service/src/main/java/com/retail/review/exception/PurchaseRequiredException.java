package com.retail.review.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class PurchaseRequiredException extends RetailException {
    public PurchaseRequiredException() {
        super(HttpStatus.FORBIDDEN, "You can only review products you have bought and received");
    }
}
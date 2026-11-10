package com.retail.order.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class PaymentDeclinedException extends RetailException {
    public PaymentDeclinedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, "Your payment was declined (" + (reason == null ? "unknown reason" : reason)
                + "). Your cart is unchanged: please try again with another payment method.");
    }
}
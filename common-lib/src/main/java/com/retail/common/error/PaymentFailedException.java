package com.retail.common.error;

import org.springframework.http.HttpStatus;

public class PaymentFailedException extends RetailException{
    public PaymentFailedException(String resource,Object id){
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                String.format("Payment failed for resource %s with id %s", resource, id));
    }
}

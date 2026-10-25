package com.retail.common.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends RetailException{
    public BusinessRuleException(String resource,Object id){
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                String.format("Business rule violation for resource %s with id %s", resource, id));
    }
}

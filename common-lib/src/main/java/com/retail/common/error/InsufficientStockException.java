package com.retail.common.error;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends RetailException{
    public InsufficientStockException(String resource,Object id){
        super(HttpStatus.CONFLICT,resource +" has insufficient stock for id "+id);
    }
}

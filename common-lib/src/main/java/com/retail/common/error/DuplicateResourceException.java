package com.retail.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends RetailException{
    public DuplicateResourceException(String resource,Object id){
        super(HttpStatus.CONFLICT,resource+" already exists with id "+id);
    }
}

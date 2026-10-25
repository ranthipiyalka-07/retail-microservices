package com.retail.common.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends RetailException{
    public ResourceNotFoundException(String resource,Object id){
        super(HttpStatus.NOT_FOUND,resource+" not found with id "+id);
    }
}


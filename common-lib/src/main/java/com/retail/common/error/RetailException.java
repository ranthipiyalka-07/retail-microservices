package com.retail.common.error;

import org.springframework.http.HttpStatus;

public abstract class RetailException extends RuntimeException{
   private final HttpStatus status;

   protected RetailException(HttpStatus status,String message){
       super(message);
       this.status=status;

   }

   public HttpStatus getStatus(){
       return status;
   }
}

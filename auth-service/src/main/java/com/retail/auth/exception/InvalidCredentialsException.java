package com.retail.auth.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends RetailException{
   public InvalidCredentialsException(){
       super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
   }
}

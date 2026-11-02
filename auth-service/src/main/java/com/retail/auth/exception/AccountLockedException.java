package com.retail.auth.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

public class AccountLockedException extends RetailException {
    public AccountLockedException() { super(HttpStatus.LOCKED, "Account is temporarily locked. Try again later."); }
}
package com.retail.inventory.exception;

import com.retail.common.error.RetailException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class StockShortageException extends RetailException {
    public StockShortageException(List<String> shortages) {
        super(HttpStatus.CONFLICT, "Not enough stock for: " + String.join("; ", shortages));
    }
}
package com.retail.review.exception;

/** The rating summary could not be pushed to product-service. Never reaches the customer: it is retried by a job. */
public class ProductSyncException extends RuntimeException {
    public ProductSyncException(Throwable cause) {
        super("Could not update the rating in product-service", cause);
    }
}
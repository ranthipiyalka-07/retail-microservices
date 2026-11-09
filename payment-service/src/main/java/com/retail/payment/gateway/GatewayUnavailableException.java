package com.retail.payment.gateway;

/** The outcome of a gateway call is unknown: timeout, outage, open circuit breaker. */
public class GatewayUnavailableException extends RuntimeException {
    public GatewayUnavailableException(String message) { super(message); }
    public GatewayUnavailableException(String message, Throwable cause) { super(message, cause); }
}
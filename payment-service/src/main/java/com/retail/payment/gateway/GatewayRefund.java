package com.retail.payment.gateway;

public record GatewayRefund(boolean approved, String reference, String responseCode, String message) {

    public static GatewayRefund ok(String reference) {
        return new GatewayRefund(true, reference, "approved", "Approved");
    }

    public static GatewayRefund declined(String code, String message) {
        return new GatewayRefund(false, null, code, message);
    }
}
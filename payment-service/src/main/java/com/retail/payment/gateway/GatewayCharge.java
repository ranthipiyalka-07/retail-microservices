package com.retail.payment.gateway;

public record GatewayCharge(boolean approved, String reference, String cardLast4, String responseCode, String message) {

    public static GatewayCharge ok(String reference, String cardLast4) {
        return new GatewayCharge(true, reference, cardLast4, "approved", "Approved");
    }

    public static GatewayCharge declined(String code, String message) {
        return new GatewayCharge(false, null, null, code, message);
    }
}
package com.retail.order.dto;

import com.retail.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CheckoutRequest(
        @NotNull @Valid AddressInput shippingAddress,
        @Valid AddressInput billingAddress,                    // empty = same as shipping
        @NotNull PaymentMethod paymentMethod,
        @NotBlank @Size(max = 255) String paymentToken,        // produced by the gateway's hosted form: never a card number
        @DecimalMin("0.01") BigDecimal expectedTotal) {        // optional: the total the customer saw

    @Override
    public String toString() {                                  // the token is a credential: never print it
        return "CheckoutRequest[method=" + paymentMethod + "]";
    }
}
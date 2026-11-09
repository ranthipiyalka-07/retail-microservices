package com.retail.payment.gateway;

import com.retail.payment.domain.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

    private static ChargeCommand command(String key, String token) {
        return new ChargeCommand(key, 1L, new BigDecimal("10.00"), "USD", PaymentMethod.CARD, token);
    }

    @Test
    void approvedTokenGivesAReferenceAndTheLast4FromTheToken() {
        GatewayCharge result = gateway.charge(command("k1", "tok_ok_1881"));

        assertThat(result.approved()).isTrue();
        assertThat(result.reference()).startsWith("sim_ch_");
        assertThat(result.cardLast4()).isEqualTo("1881");
        assertThat(gateway.charge(command("k2", "tok_ok")).cardLast4()).isEqualTo("4242");
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameResultEvenWithAnotherToken() {
        GatewayCharge first = gateway.charge(command("k1", "tok_ok_1111"));
        GatewayCharge second = gateway.charge(command("k1", "tok_declined"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void declineScenariosReturnADeclineAndNotAnError() {
        assertThat(gateway.charge(command("a", "tok_insufficient")).responseCode()).isEqualTo("insufficient_funds");
        assertThat(gateway.charge(command("b", "tok_declined")).responseCode()).isEqualTo("card_declined");
        assertThat(gateway.charge(command("c", "something-else")).responseCode()).isEqualTo("invalid_token");
        assertThat(gateway.charge(command("a", "tok_ok")).approved()).isFalse();      // the first answer is kept
    }

    @Test
    void timeoutScenarioFailsTheCallButTheChargeCanBeLookedUp() {
        assertThatThrownBy(() -> gateway.charge(command("k1", "tok_timeout")))
                .isInstanceOf(GatewayUnavailableException.class);

        assertThat(gateway.findCharge("k1")).isPresent();
        assertThat(gateway.findCharge("k1").get().approved()).isTrue();
    }

    @Test
    void errorScenarioLeavesNoRecordAndCanBeRetriedWithAnotherToken() {
        assertThatThrownBy(() -> gateway.charge(command("k1", "tok_error")))
                .isInstanceOf(GatewayUnavailableException.class);

        assertThat(gateway.findCharge("k1")).isEmpty();
        assertThat(gateway.charge(command("k1", "tok_ok")).approved()).isTrue();
    }

    @Test
    void refundsAreIdempotentAndUnknownChargesAreDeclined() {
        GatewayRefund first = gateway.refund(new RefundCommand("r1", "sim_ch_abc", new BigDecimal("5.00"), "x"));
        GatewayRefund again = gateway.refund(new RefundCommand("r1", "sim_ch_abc", new BigDecimal("5.00"), "x"));
        GatewayRefund unknown = gateway.refund(new RefundCommand("r2", "other_123", new BigDecimal("5.00"), "x"));

        assertThat(first.approved()).isTrue();
        assertThat(again).isEqualTo(first);
        assertThat(unknown.approved()).isFalse();
        assertThat(unknown.responseCode()).isEqualTo("unknown_charge");
        assertThat(gateway.findRefund("r1")).isPresent();
    }
}
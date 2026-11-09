package com.retail.payment.service;

import com.retail.payment.config.PaymentProperties;
import com.retail.payment.domain.Payment;
import com.retail.payment.domain.PaymentStatus;
import com.retail.payment.gateway.GatewayAccess;
import com.retail.payment.gateway.GatewayCharge;
import com.retail.payment.repository.PaymentRepository;
import com.retail.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingPaymentReconcilerTest {

    @Mock PaymentRepository payments;
    @Mock RefundRepository refunds;
    @Mock GatewayAccess gateway;
    @Mock PaymentService paymentService;

    private PendingPaymentReconciler reconciler;

    @BeforeEach
    void setUp() {
        var props = new PaymentProperties("USD", new BigDecimal("10000.00"), Duration.ofMinutes(2), Duration.ofMinutes(15));
        reconciler = new PendingPaymentReconciler(payments, refunds, gateway, paymentService, props);
    }

    private void unfinishedPayment(Duration age) {
        Payment p = new Payment();
        p.setId(10L);
        p.setIdempotencyKey("key-1");
        p.setStatus(PaymentStatus.INITIATED);
        p.setCreatedAt(Instant.now().minus(age));
        when(payments.findByStatusAndCreatedAtBefore(eq(PaymentStatus.INITIATED), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(p));
    }

    @Test
    void aChargeTheGatewayKnowsIsSettledWithTheGatewaysAnswer() {
        unfinishedPayment(Duration.ofMinutes(5));
        GatewayCharge answer = GatewayCharge.ok("sim_ch_1", "4242");
        when(gateway.findCharge("key-1")).thenReturn(Optional.of(answer));

        reconciler.reconcile();

        verify(paymentService).settleCharge(10L, answer);
    }

    @Test
    void anUnknownChargeIsLeftAloneUntilTheGiveUpTimeHasPassed() {
        unfinishedPayment(Duration.ofMinutes(5));
        when(gateway.findCharge("key-1")).thenReturn(Optional.empty());

        reconciler.reconcile();

        verify(paymentService, never()).settleCharge(any(), any());
    }

    @Test
    void anOldChargeTheGatewayNeverSawIsClosedAsFailed() {
        unfinishedPayment(Duration.ofMinutes(20));
        when(gateway.findCharge("key-1")).thenReturn(Optional.empty());

        reconciler.reconcile();

        ArgumentCaptor<GatewayCharge> closed = ArgumentCaptor.forClass(GatewayCharge.class);
        verify(paymentService).settleCharge(eq(10L), closed.capture());
        assertThat(closed.getValue().approved()).isFalse();
        assertThat(closed.getValue().responseCode()).isEqualTo("NO_GATEWAY_RECORD");
    }
}
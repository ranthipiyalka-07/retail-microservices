
package com.retail.payment.gateway;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.PaymentResultEvent;
import com.retail.payment.config.PaymentProperties;
import com.retail.payment.domain.*;
import com.retail.payment.dto.ChargeRequest;
import com.retail.payment.dto.PaymentResponse;
import com.retail.payment.dto.RefundRequest;
import com.retail.payment.dto.RefundResponse;
import com.retail.payment.exception.ConflictException;
import com.retail.payment.exception.InvalidRequestException;
import com.retail.payment.repository.PaymentRepository;
import com.retail.payment.repository.PaymentTransactionRepository;
import com.retail.payment.repository.RefundRepository;
import com.retail.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    /**
     * Transactions do nothing in these tests:
     * only the payment logic is checked.
     */
    static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    @Mock
    PaymentRepository payments;

    @Mock
    RefundRepository refunds;

    @Mock
    PaymentTransactionRepository ledger;

    @Mock
    GatewayAccess gateway;

    @Mock
    ApplicationEventPublisher events;

    private PaymentService service;
    private Payment storedPayment;
    private Refund storedRefund;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                payments,
                refunds,
                ledger,
                gateway,
                events,
                new PaymentProperties(
                        "USD",
                        new BigDecimal("10000.00"),
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(15)
                ),
                new NoOpTransactionManager()
        );
    }

    // ---------- helpers ----------

    private static ChargeRequest charge(String key) {
        return new ChargeRequest(
                1001L,
                3L,
                new BigDecimal("49.90"),
                "USD",
                PaymentMethod.CARD,
                "tok_ok_4242",
                key
        );
    }

    private static Payment payment(long id, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setOrderId(1001L);
        p.setCustomerId(3L);
        p.setAmount(new BigDecimal("49.90"));
        p.setCurrency("USD");
        p.setMethod(PaymentMethod.CARD);
        p.setStatus(status);
        p.setIdempotencyKey("key-1");
        p.setGatewayReference("sim_ch_1");
        return p;
    }

    /**
     * A new payment gets id 10 and can then be found again by that id.
     */
    private void persistNewPaymentInMemory() {
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            storedPayment = inv.getArgument(0);
            storedPayment.setId(10L);
            return storedPayment;
        });

        when(payments.findLockedById(10L))
                .thenAnswer(inv -> Optional.ofNullable(storedPayment));
    }

    private void persistNewRefundInMemory() {
        when(refunds.saveAndFlush(any(Refund.class))).thenAnswer(inv -> {
            storedRefund = inv.getArgument(0);
            storedRefund.setId(20L);
            return storedRefund;
        });

        when(refunds.findById(20L))
                .thenAnswer(inv -> Optional.ofNullable(storedRefund));
    }

    private static RefundRequest refundRequest(String key, String amount) {
        return new RefundRequest(
                key,
                amount == null ? null : new BigDecimal(amount),
                "Customer cancelled"
        );
    }

    // ---------- charge ----------

    @Test
    void approvedChargeBecomesSuccessAndWritesLedgerRowAndEvent() {
        persistNewPaymentInMemory();

        when(gateway.charge(any(ChargeCommand.class)))
                .thenReturn(GatewayCharge.ok("sim_ch_1", "4242"));

        PaymentResponse response = service.charge(charge("key-1"));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.cardLast4()).isEqualTo("4242");
        assertThat(response.amount()).isEqualByComparingTo("49.90");

        ArgumentCaptor<ChargeCommand> command =
                ArgumentCaptor.forClass(ChargeCommand.class);

        verify(gateway).charge(command.capture());

        assertThat(command.getValue().idempotencyKey()).isEqualTo("key-1");
        assertThat(command.getValue().paymentToken()).isEqualTo("tok_ok_4242");

        ArgumentCaptor<PaymentTransaction> row =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(ledger).save(row.capture());

        assertThat(row.getValue().getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(row.getValue().getStatus())
                .isEqualTo(com.retail.payment.domain.TransactionStatus.SUCCESS);

        ArgumentCaptor<PaymentResultEvent> event =
                ArgumentCaptor.forClass(PaymentResultEvent.class);

        verify(events).publishEvent(event.capture());

        assertThat(event.getValue().status())
                .isEqualTo(PaymentResultEvent.Status.SUCCESS);

        assertThat(event.getValue().orderId()).isEqualTo(1001L);
    }

    @Test
    void declinedChargeBecomesFailedWithTheGatewayCode() {
        persistNewPaymentInMemory();

        when(gateway.charge(any(ChargeCommand.class)))
                .thenReturn(GatewayCharge.declined(
                        "insufficient_funds",
                        "The card has insufficient funds"
                ));

        PaymentResponse response = service.charge(charge("key-1"));

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("insufficient_funds");

        ArgumentCaptor<PaymentResultEvent> event =
                ArgumentCaptor.forClass(PaymentResultEvent.class);

        verify(events).publishEvent(event.capture());

        assertThat(event.getValue().status())
                .isEqualTo(PaymentResultEvent.Status.FAILED);

        assertThat(event.getValue().failureReason())
                .isEqualTo("insufficient_funds");
    }

    @Test
    void gatewayOutageLeavesThePaymentInitiatedWithoutLedgerRowOrEvent() {
        persistNewPaymentInMemory();

        when(gateway.charge(any(ChargeCommand.class)))
                .thenThrow(new GatewayUnavailableException("down"));

        PaymentResponse response = service.charge(charge("key-1"));

        assertThat(response.status()).isEqualTo(PaymentStatus.INITIATED);

        verifyNoInteractions(ledger, events);
        verify(payments, never()).findLockedById(any());
    }

    @Test
    void replayOfAFinishedPaymentReturnsItWithoutCallingTheGateway() {
        when(payments.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(payment(10L, PaymentStatus.SUCCESS)));

        PaymentResponse response = service.charge(charge("key-1"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);

        verifyNoInteractions(gateway, ledger, events);
    }

    @Test
    void sameKeyWithADifferentAmountIsRejected() {
        when(payments.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(payment(10L, PaymentStatus.SUCCESS)));

        var other = new ChargeRequest(
                1001L,
                3L,
                new BigDecimal("10.00"),
                "USD",
                PaymentMethod.CARD,
                "tok_ok",
                "key-1"
        );

        assertThatThrownBy(() -> service.charge(other))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(gateway);
    }

    @Test
    void anOrderThatIsAlreadyPaidCannotBeChargedWithAnotherKey() {
        when(payments.existsByOrderIdAndStatusIn(eq(1001L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.charge(charge("key-2")))
                .isInstanceOf(ConflictException.class);

        verify(payments, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(gateway);
    }

    @Test
    void aParallelRequestWithTheSameKeyIsRepeatedAndFindsTheWinnersPayment() {

        Payment winner = payment(10L, PaymentStatus.INITIATED);

        // First lookup: no payment found.
        // Second lookup: the other request's payment is found.
        when(payments.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));

        // Simulate the unique idempotency-key constraint:
        // another request inserted the payment first.
        when(payments.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(winner));

        when(gateway.charge(any(ChargeCommand.class)))
                .thenReturn(GatewayCharge.ok("sim_ch_1", "4242"));

        PaymentResponse response = service.charge(charge("key-1"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);

        // The gateway is called for the winner's existing INITIATED payment.
        verify(gateway).charge(any(ChargeCommand.class));

        // The payment is updated without another saveAndFlush().
        verify(payments).save(winner);

        // The ledger row is written once.
        verify(ledger).save(any(PaymentTransaction.class));
    }

    @Test
    void cashOnDeliveryAndOtherCurrenciesAndHugeAmountsAreRejected() {
        var cod = new ChargeRequest(
                1001L, 3L, new BigDecimal("49.90"),
                "USD", PaymentMethod.COD, "tok", "key-1"
        );

        var euro = new ChargeRequest(
                1001L, 3L, new BigDecimal("49.90"),
                "EUR", PaymentMethod.CARD, "tok", "key-1"
        );

        var huge = new ChargeRequest(
                1001L, 3L, new BigDecimal("20000.00"),
                "USD", PaymentMethod.CARD, "tok", "key-1"
        );

        assertThatThrownBy(() -> service.charge(cod))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> service.charge(euro))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> service.charge(huge))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(gateway, payments);
    }

    @Test
    void settlingAnAlreadySettledPaymentChangesNothing() {
        Payment done = payment(10L, PaymentStatus.SUCCESS);

        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(done));

        PaymentResponse response = service.settleCharge(
                10L,
                GatewayCharge.declined("late", "late")
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);

        verifyNoInteractions(ledger, events);
    }

    // ---------- refund ----------

    @Test
    void refundWithoutAnAmountRefundsEverythingAndMarksThePaymentRefunded() {
        Payment paid = payment(10L, PaymentStatus.SUCCESS);

        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(paid));

        persistNewRefundInMemory();

        when(refunds.sumAmount(eq(10L), any()))
                .thenReturn(BigDecimal.ZERO, new BigDecimal("49.90"));

        when(gateway.refund(any(RefundCommand.class)))
                .thenReturn(GatewayRefund.ok("sim_re_1"));

        RefundResponse response = service.refund(
                10L,
                refundRequest("refund-1", null)
        );

        assertThat(response.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(response.amount()).isEqualByComparingTo("49.90");
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        ArgumentCaptor<RefundCommand> command =
                ArgumentCaptor.forClass(RefundCommand.class);

        verify(gateway).refund(command.capture());

        assertThat(command.getValue().chargeReference()).isEqualTo("sim_ch_1");
        assertThat(command.getValue().idempotencyKey()).isEqualTo("refund-1");

        ArgumentCaptor<PaymentTransaction> row =
                ArgumentCaptor.forClass(PaymentTransaction.class);

        verify(ledger).save(row.capture());

        assertThat(row.getValue().getType()).isEqualTo(TransactionType.REFUND);
        assertThat(row.getValue().getRefundId()).isEqualTo(20L);
    }

    @Test
    void partialRefundMarksThePaymentPartiallyRefunded() {
        Payment paid = payment(10L, PaymentStatus.SUCCESS);

        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(paid));

        persistNewRefundInMemory();

        when(refunds.sumAmount(eq(10L), any()))
                .thenReturn(BigDecimal.ZERO, new BigDecimal("20.00"));

        when(gateway.refund(any(RefundCommand.class)))
                .thenReturn(GatewayRefund.ok("sim_re_1"));

        RefundResponse response = service.refund(
                10L,
                refundRequest("refund-1", "20.00")
        );

        assertThat(response.amount()).isEqualByComparingTo("20.00");

        assertThat(response.paymentStatus())
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void refundingMoreThanTheRemainingAmountIsRejected() {
        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(
                        payment(10L, PaymentStatus.PARTIALLY_REFUNDED)
                ));

        when(refunds.sumAmount(eq(10L), any()))
                .thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() ->
                service.refund(10L, refundRequest("refund-2", "25.00"))
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("19.90");

        verifyNoInteractions(gateway, ledger);
    }

    @Test
    void aPaymentThatWasNeverPaidCannotBeRefunded() {
        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(payment(10L, PaymentStatus.FAILED)));

        assertThatThrownBy(() ->
                service.refund(10L, refundRequest("refund-1", null))
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void refundingAnUnknownPaymentGivesNotFound() {
        when(payments.findLockedById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.refund(99L, refundRequest("refund-1", null))
        )
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gatewayOutageLeavesTheRefundPending() {
        when(payments.findLockedById(10L))
                .thenReturn(Optional.of(payment(10L, PaymentStatus.SUCCESS)));

        persistNewRefundInMemory();

        when(gateway.refund(any(RefundCommand.class)))
                .thenThrow(new GatewayUnavailableException("down"));

        RefundResponse response = service.refund(
                10L,
                refundRequest("refund-1", null)
        );

        assertThat(response.status()).isEqualTo(RefundStatus.PENDING);

        verifyNoInteractions(ledger);
    }

    @Test
    void replayOfAFinishedRefundReturnsItWithoutCallingTheGateway() {
        Refund done = new Refund();
        done.setId(20L);
        done.setPaymentId(10L);
        done.setAmount(new BigDecimal("20.00"));
        done.setReason("Damaged");
        done.setStatus(RefundStatus.SUCCESS);
        done.setIdempotencyKey("refund-1");

        when(refunds.findByIdempotencyKey("refund-1"))
                .thenReturn(Optional.of(done));

        when(payments.findById(10L))
                .thenReturn(Optional.of(
                        payment(10L, PaymentStatus.PARTIALLY_REFUNDED)
                ));

        RefundResponse response = service.refund(
                10L,
                refundRequest("refund-1", null)
        );

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(RefundStatus.SUCCESS);

        verifyNoInteractions(gateway, ledger);
        verify(payments, never()).findLockedById(any());
    }

    @Test
    void sameRefundKeyWithADifferentAmountIsRejected() {
        Refund done = new Refund();
        done.setId(20L);
        done.setPaymentId(10L);
        done.setAmount(new BigDecimal("20.00"));
        done.setStatus(RefundStatus.SUCCESS);
        done.setIdempotencyKey("refund-1");

        when(refunds.findByIdempotencyKey("refund-1"))
                .thenReturn(Optional.of(done));

        assertThatThrownBy(() ->
                service.refund(10L, refundRequest("refund-1", "5.00"))
        )
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(gateway);
    }
}

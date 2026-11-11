package com.retail.order.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.order.client.*;
import com.retail.order.config.OrderProperties;
import com.retail.order.domain.CustomerOrder;
import com.retail.order.domain.DueFlag;
import com.retail.order.domain.OrderStatus;
import com.retail.order.domain.PaymentMethod;
import com.retail.order.dto.AddressInput;
import com.retail.order.dto.CheckoutRequest;
import com.retail.order.exception.ConflictException;
import com.retail.order.exception.PaymentDeclinedException;
import com.retail.order.exception.RemoteUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderSagaTest {

    private static final String KEY = "checkout-0001";

    @Mock OrderStateService state;
    @Mock Downstream downstream;

    private OrderSaga saga;
    private CustomerOrder order;                    // the one "database row" of these tests

    @BeforeEach
    void setUp() {
        OrderProperties props = new OrderProperties("USD", new BigDecimal("0.10"), new BigDecimal("5.00"),
                new BigDecimal("100.00"), Duration.ofMinutes(2), Duration.ofMinutes(10), 30);
        saga = new OrderSaga(state, downstream, new PriceCalculator(props), props);

        order = new CustomerOrder();
        order.setId(1L);
        order.setOrderNumber("ORD-TEST");
        order.setCustomerId(5L);
        order.setCartId(77L);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());

        when(state.find(1L)).thenAnswer(inv -> Optional.of(order));
        when(state.findByKey(5L, KEY)).thenReturn(Optional.empty());
        when(state.createPending(any(NewOrder.class))).thenReturn(new OrderStateService.Created(order, true));
        doAnswer(inv -> { order.setPaymentId(inv.getArgument(1)); return null; })
                .when(state).recordPayment(eq(1L), any());
        when(state.confirm(1L)).thenAnswer(inv -> {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setCartCheckoutDue(true);
            return true;
        });
        when(state.fail(eq(1L), any(OrderStatus.class), any(), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
            order.setStatus(inv.getArgument(1));
            order.setFailureReason(inv.getArgument(2));
            order.setRefundDue(inv.<Boolean>getArgument(3));
            order.setStockReturnDue(inv.<Boolean>getArgument(4));
            return true;
        });
        when(state.cancel(eq(1L), any())).thenAnswer(inv -> {
            order.setStatus(OrderStatus.CANCELLED);
            order.setRefundDue(true);
            order.setStockReturnDue(true);
            return true;
        });
        doAnswer(inv -> {
            switch ((DueFlag) inv.getArgument(1)) {
                case REFUND -> order.setRefundDue(false);
                case STOCK_RETURN -> order.setStockReturnDue(false);
                case CART_CHECKOUT -> order.setCartCheckoutDue(false);
            }
            return null;
        }).when(state).clear(eq(1L), any(DueFlag.class));
    }

    // ---------- helpers ----------

    private static CheckoutRequest request(BigDecimal expectedTotal) {
        AddressInput address = new AddressInput("John Doe", "12 Galle Road", null, "Bentota", null, "80500", "LK", "+94771234567");
        return new CheckoutRequest(address, null, PaymentMethod.CARD, "tok_ok_4242", expectedTotal);
    }

    private void cartWithTee() {
        when(downstream.cart(5L)).thenReturn(new CartClient.CartData(77L,
                List.of(new CartClient.CartData.Line(10L, "Tee", new BigDecimal("19.99"), 2))));
        when(downstream.product(10L)).thenReturn(Optional.of(
                new ProductClient.ProductData(10L, "TEE-001", "Classic Tee", 3L, new BigDecimal("19.99"))));
        when(downstream.reserve(eq(1L), any())).thenReturn(Downstream.ReserveResult.success());
    }

    private static PaymentClient.PaymentData payment(String status, String failureReason) {
        return new PaymentClient.PaymentData(9L, 1L, new BigDecimal("48.98"), status, failureReason);
    }

    private OrderSaga.PlaceResult place() {
        return saga.checkout(5L, "john@example.com", KEY, request(null));
    }

    // ---------- checkout ----------

    @Test
    void paidOrderIsConfirmedAndTheCartIsClosed() {
        cartWithTee();
        when(downstream.charge(any())).thenReturn(payment("SUCCESS", null));
        when(downstream.commit(1L)).thenReturn(Downstream.CommitResult.COMMITTED);

        OrderSaga.PlaceResult result = place();

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.created()).isTrue();
        verify(downstream).checkoutCart(5L, 77L);
        verify(state).clear(1L, DueFlag.CART_CHECKOUT);
        assertThat(order.getPaymentId()).isEqualTo(9L);
    }

    @Test
    void theChargeUsesTheServerSideTotalAndAnIdempotencyKeyPerOrder() {
        cartWithTee();
        when(downstream.charge(any())).thenReturn(payment("SUCCESS", null));
        when(downstream.commit(1L)).thenReturn(Downstream.CommitResult.COMMITTED);

        place();

        var captor = org.mockito.ArgumentCaptor.forClass(PaymentClient.ChargeRequest.class);
        verify(downstream).charge(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("48.98");     // 39.98 + 4.00 tax + 5.00 shipping
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("order-1-pay");
        assertThat(captor.getValue().orderId()).isEqualTo(1L);
        assertThat(captor.getValue().currency()).isEqualTo("USD");
    }

    @Test
    void outOfStockFailsTheOrderWithoutChargingAnything() {
        cartWithTee();
        when(downstream.reserve(eq(1L), any())).thenReturn(Downstream.ReserveResult.shortage("Not enough stock for: product 10"));

        assertThatThrownBy(this::place).isInstanceOf(ConflictException.class).hasMessageContaining("Not enough stock");
        verify(state).fail(eq(1L), eq(OrderStatus.FAILED), org.mockito.ArgumentMatchers.startsWith("OUT_OF_STOCK"), eq(false), eq(false));
        verify(downstream, never()).charge(any());
    }

    @Test
    void inventoryOutageFailsTheOrderAndReleasesAHoldThatMayExist() {
        cartWithTee();
        when(downstream.reserve(eq(1L), any())).thenThrow(new RemoteUnavailableException("inventory"));

        assertThatThrownBy(this::place).isInstanceOf(RemoteUnavailableException.class);
        verify(state).fail(1L, OrderStatus.FAILED, "INVENTORY_UNAVAILABLE", false, true);
        verify(downstream).release(1L);
        verify(downstream, never()).charge(any());
    }

    @Test
    void declinedPaymentGivesAnErrorAndReleasesTheStock() {
        cartWithTee();
        when(downstream.charge(any())).thenReturn(payment("FAILED", "card_declined"));

        assertThatThrownBy(this::place).isInstanceOf(PaymentDeclinedException.class).hasMessageContaining("card_declined");
        verify(downstream).release(1L);
        verify(state).clear(1L, DueFlag.STOCK_RETURN);
        verify(downstream, never()).commit(any());
    }

    @Test
    void unknownPaymentOutcomeLeavesTheOrderPendingAndTouchesNothing() {
        cartWithTee();
        when(downstream.charge(any())).thenThrow(new RemoteUnavailableException("payment"));

        OrderSaga.PlaceResult result = place();

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verify(state, never()).fail(any(), any(), any(), anyBoolean(), anyBoolean());
        verify(downstream, never()).release(any());
    }

    @Test
    void anInitiatedPaymentKeepsTheOrderPending() {
        cartWithTee();
        when(downstream.charge(any())).thenReturn(payment("INITIATED", null));

        OrderSaga.PlaceResult result = place();

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verify(downstream, never()).commit(any());
        verify(state, never()).confirm(any());
    }

    @Test
    void paymentThatSucceedsAfterTheReservationExpiredIsFailedAndRefunded() {
        cartWithTee();
        when(downstream.charge(any())).thenReturn(payment("SUCCESS", null));
        when(downstream.commit(1L)).thenReturn(Downstream.CommitResult.EXPIRED);
        when(downstream.payment(9L)).thenReturn(Optional.of(payment("SUCCESS", null)));
        when(downstream.refund(eq(9L), eq("order-1-refund"), any())).thenReturn(new Downstream.RefundResult("SUCCESS"));

        assertThatThrownBy(this::place).isInstanceOf(ConflictException.class).hasMessageContaining("RESERVATION_EXPIRED");
        verify(state).fail(1L, OrderStatus.FAILED, "RESERVATION_EXPIRED", true, false);
        verify(downstream).refund(eq(9L), eq("order-1-refund"), any());
        verify(state).clear(1L, DueFlag.REFUND);
        verify(downstream, never()).release(any());
    }

    @Test
    void emptyCartIsRejectedBeforeAnythingIsCreated() {
        when(downstream.cart(5L)).thenReturn(new CartClient.CartData(null, List.of()));

        assertThatThrownBy(this::place).isInstanceOf(ConflictException.class).hasMessageContaining("empty");
        verify(state, never()).createPending(any());
    }

    @Test
    void productThatIsNoLongerAvailableIsRejected() {
        cartWithTee();
        when(downstream.product(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(this::place).isInstanceOf(ConflictException.class).hasMessageContaining("Tee");
        verify(state, never()).createPending(any());
    }

    @Test
    void changedPriceIsRefusedWhenTheCustomerSentTheTotalTheySaw() {
        cartWithTee();

        assertThatThrownBy(() -> saga.checkout(5L, "john@example.com", KEY, request(new BigDecimal("30.00"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Prices have changed");
        verify(state, never()).createPending(any());
    }

    @Test
    void retryWithTheSameKeyReturnsTheExistingOrderWithoutReadingTheCart() {
        order.setStatus(OrderStatus.CONFIRMED);
        when(state.findByKey(5L, KEY)).thenReturn(Optional.of(order));

        OrderSaga.PlaceResult result = place();

        assertThat(result.created()).isFalse();
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(downstream, never()).cart(any());
        verify(downstream, never()).charge(any());
    }

    @Test
    void retryOfADeclinedOrderGivesTheSameDeclineAgain() {
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setFailureReason("card_declined");
        when(state.findByKey(5L, KEY)).thenReturn(Optional.of(order));

        assertThatThrownBy(this::place).isInstanceOf(PaymentDeclinedException.class);
    }

    @Test
    void parallelRequestWithTheSameKeyIsAnsweredWithTheWinnersOrder() {
        cartWithTee();
        order.setStatus(OrderStatus.CONFIRMED);
        when(state.createPending(any(NewOrder.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(state.findByKey(5L, KEY)).thenReturn(Optional.empty(), Optional.of(order));

        OrderSaga.PlaceResult result = place();

        assertThat(result.created()).isFalse();
        verify(downstream, never()).reserve(any(), any());
    }

    @Test
    void anotherCheckoutOfTheSameCartIsAConflict() {
        cartWithTee();
        when(state.createPending(any(NewOrder.class))).thenThrow(new DataIntegrityViolationException("duplicate cart"));
        when(state.findByKey(5L, KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(this::place).isInstanceOf(ConflictException.class).hasMessageContaining("already in progress");
    }

    // ---------- settle (the reconciler path) ----------

    @Test
    void pendingOrderWithAnApprovedPaymentIsConfirmedByTheReconciler() {
        when(downstream.paymentOfOrder(1L)).thenReturn(Optional.of(payment("SUCCESS", null)));
        when(downstream.commit(1L)).thenReturn(Downstream.CommitResult.COMMITTED);

        saga.settle(1L, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(downstream).checkoutCart(5L, 77L);
    }

    @Test
    void pendingOrderWithoutAnyPaymentIsFailedOnlyAfterTheGiveUpTime() {
        when(downstream.paymentOfOrder(1L)).thenReturn(Optional.empty());

        saga.settle(1L, null);
        verify(state, never()).fail(any(), any(), any(), anyBoolean(), anyBoolean());

        order.setCreatedAt(Instant.now().minus(Duration.ofMinutes(11)));
        saga.settle(1L, null);
        verify(state).fail(1L, OrderStatus.FAILED, "PAYMENT_NOT_STARTED", false, true);
        verify(downstream).release(1L);
    }

    @Test
    void paymentServiceOutageDuringSettlingChangesNothing() {
        when(downstream.paymentOfOrder(1L)).thenThrow(new RemoteUnavailableException("payment"));

        saga.settle(1L, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ---------- cancel ----------

    @Test
    void cancellingAConfirmedOrderReturnsTheStockAndRefundsThePayment() {
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentId(9L);
        when(state.findOwned(5L, 1L)).thenReturn(Optional.of(order));
        when(downstream.payment(9L)).thenReturn(Optional.of(payment("SUCCESS", null)));
        when(downstream.refund(eq(9L), eq("order-1-refund"), any())).thenReturn(new Downstream.RefundResult("SUCCESS"));

        saga.cancel(5L, 1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(downstream).release(1L);
        verify(state).clear(1L, DueFlag.STOCK_RETURN);
        verify(state).clear(1L, DueFlag.REFUND);
    }

    @Test
    void failedRefundKeepsTheFlagSoTheReconcilerRetries() {
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentId(9L);
        when(state.findOwned(5L, 1L)).thenReturn(Optional.of(order));
        when(downstream.payment(9L)).thenReturn(Optional.of(payment("SUCCESS", null)));
        when(downstream.refund(eq(9L), any(), any())).thenThrow(new RemoteUnavailableException("payment"));

        saga.cancel(5L, 1L);

        assertThat(order.isRefundDue()).isTrue();
        verify(state, never()).clear(1L, DueFlag.REFUND);
        verify(state).recordCompensationFailure(1L);
    }

    @Test
    void ordersThatCannotBeCancelledAreRefused() {
        when(state.findOwned(5L, 1L)).thenReturn(Optional.of(order));

        order.setStatus(OrderStatus.PENDING);
        assertThatThrownBy(() -> saga.cancel(5L, 1L)).isInstanceOf(ConflictException.class);
        order.setStatus(OrderStatus.PACKED);
        assertThatThrownBy(() -> saga.cancel(5L, 1L)).isInstanceOf(ConflictException.class);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        assertThatThrownBy(() -> saga.cancel(5L, 1L)).isInstanceOf(ConflictException.class);
        verify(state, never()).cancel(any(), any());
    }

    @Test
    void anotherCustomersOrderIsNotFound() {
        when(state.findOwned(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saga.cancel(99L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aCampaignDiscountLowersTheChargedTotalAndIsStoredOnTheLine() {
        cartWithTee();                                                         // 2 x 19.99
        when(downstream.prices(any())).thenReturn(List.of(new CampaignClient.PriceData(10L, new BigDecimal("19.99"),
                new BigDecimal("15.99"), new BigDecimal("4.00"), 3L, "Tee Sale")));
        when(downstream.charge(any())).thenReturn(payment("SUCCESS", null));
        when(downstream.commit(1L)).thenReturn(Downstream.CommitResult.COMMITTED);

        place();

        var charge = org.mockito.ArgumentCaptor.forClass(PaymentClient.ChargeRequest.class);
        verify(downstream).charge(charge.capture());
        // gross 39.98, discount 8.00, net 31.98, tax 3.20, shipping 5.00
        assertThat(charge.getValue().amount()).isEqualByComparingTo("40.18");

        var created = org.mockito.ArgumentCaptor.forClass(NewOrder.class);
        verify(state).createPending(created.capture());
        assertThat(created.getValue().totals().discount()).isEqualByComparingTo("8.00");
        assertThat(created.getValue().lines().get(0).unitPrice()).isEqualByComparingTo("19.99");
        assertThat(created.getValue().lines().get(0).lineDiscount()).isEqualByComparingTo("8.00");
        assertThat(created.getValue().lines().get(0).lineTotal()).isEqualByComparingTo("31.98");
    }

    @Test
    void campaignServiceOutageFailsTheCheckoutBeforeAnythingIsCreated() {
        cartWithTee();
        when(downstream.prices(any())).thenThrow(new RemoteUnavailableException("campaign"));

        assertThatThrownBy(this::place).isInstanceOf(RemoteUnavailableException.class);
        verify(state, never()).createPending(any());
        verify(downstream, never()).charge(any());
    }
}
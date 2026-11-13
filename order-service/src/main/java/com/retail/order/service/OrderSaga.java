package com.retail.order.service;

import com.retail.common.audit.Audit;
import com.retail.common.error.ResourceNotFoundException;
import com.retail.order.client.CartClient;
import com.retail.order.client.Downstream;
import com.retail.order.client.InventoryClient;
import com.retail.order.client.PaymentClient;
import com.retail.order.config.OrderProperties;
import com.retail.order.domain.CustomerOrder;
import com.retail.order.domain.DueFlag;
import com.retail.order.domain.OrderStatus;
import com.retail.order.dto.CheckoutRequest;
import com.retail.order.exception.ConflictException;
import com.retail.order.exception.PaymentDeclinedException;
import com.retail.order.exception.RemoteUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.retail.order.client.CampaignClient;
import com.retail.order.client.ProductClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Not @Transactional on purpose: remote calls never run inside a database transaction. */
@Service
public class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);

    public record PlaceResult(Long orderId, OrderStatus status, boolean created) {}

    private final OrderStateService state;
    private final Downstream downstream;
    private final PriceCalculator calculator;
    private final OrderProperties props;

    public OrderSaga(OrderStateService state, Downstream downstream, PriceCalculator calculator, OrderProperties props) {
        this.state = state;
        this.downstream = downstream;
        this.calculator = calculator;
        this.props = props;
    }

    // ================= checkout =================

    public PlaceResult checkout(Long customerId, String customerEmail, String idempotencyKey, CheckoutRequest request) {
        // A retry must be answered before the cart is read: the first call may already have emptied it.
        Optional<CustomerOrder> existing = state.findByKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return outcome(existing.get(), false);
        }

        CartClient.CartData cart = downstream.cart(customerId);
        if (cart == null || cart.cartId() == null || cart.items() == null || cart.items().isEmpty()) {
            throw new ConflictException("Your cart is empty");
        }
        List<PricedLine> lines = price(cart);
        PriceCalculator.Totals totals = calculator.compute(lines);
        if (request.expectedTotal() != null && request.expectedTotal().compareTo(totals.grandTotal()) != 0) {
            throw new ConflictException("Prices have changed: the order total is now "
                    + totals.grandTotal().toPlainString() + " " + props.currency() + ". Please review your cart.");
        }

        OrderStateService.Created created;
        try {
            created = state.createPending(new NewOrder(customerId, customerEmail, cart.cartId(), idempotencyKey,
                    props.currency(), lines, totals, request.shippingAddress(), request.billingAddress()));
        } catch (DataIntegrityViolationException e) {
            Optional<CustomerOrder> winner = state.findByKey(customerId, idempotencyKey);
            if (winner.isPresent()) {
                return outcome(winner.get(), false);               // a parallel request with the same key won
            }
            throw new ConflictException("Another checkout of this cart is already in progress. "
                    + "Please check your orders in a moment.");
        }
        if (!created.isNew()) {
            return outcome(created.order(), false);
        }
        Long orderId = created.order().getId();

        // 1. reserve the stock
        Downstream.ReserveResult reserved;
        try {
            reserved = downstream.reserve(orderId, lines.stream()
                    .map(l -> new InventoryClient.ReserveRequest.Item(l.productId(), l.quantity())).toList());
        } catch (RemoteUnavailableException e) {
            // The reservation may exist even though the answer was lost: fail the order and release it.
            state.fail(orderId, OrderStatus.FAILED, "INVENTORY_UNAVAILABLE", false, true);
            compensate(orderId);
            throw e;
        }
        if (!reserved.ok()) {
            state.fail(orderId, OrderStatus.FAILED, "OUT_OF_STOCK: " + reserved.message(), false, false);
            throw new ConflictException(reserved.message());
        }

        // 2. charge. One charge per order: a new attempt is a new order with a new idempotency key.
        PaymentClient.PaymentData payment;
        try {
            payment = downstream.charge(new PaymentClient.ChargeRequest(orderId, customerId,
                    totals.grandTotal(), props.currency(), request.paymentMethod().name(),
                    request.paymentToken(), "order-" + orderId + "-pay"));
        } catch (RemoteUnavailableException e) {
            log.warn("Payment outcome unknown: orderId={}. The order stays PENDING and the reconciler settles it.", orderId);
            return new PlaceResult(orderId, OrderStatus.PENDING, true);
        }

        // 3. act on the answer
        settle(orderId, payment);
        CustomerOrder after = state.find(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        Audit.record("order.place", after.getStatus() == OrderStatus.CONFIRMED ? Audit.Outcome.SUCCESS : Audit.Outcome.FAILURE, "order", orderId, "status=" + after.getStatus());
        return outcome(after, true);
    }

    /** A FAILED or PAYMENT_FAILED order answers with the same error again. */
    private PlaceResult outcome(CustomerOrder order, boolean created) {
        CustomerOrder current = order;
        if (!created && order.getStatus() == OrderStatus.PENDING) {
            settle(order.getId(), null);                           // a retry may be able to finish the order
            current = state.find(order.getId()).orElse(order);
        }
        return switch (current.getStatus()) {
            case PAYMENT_FAILED -> throw new PaymentDeclinedException(current.getFailureReason());
            case FAILED -> throw new ConflictException("Order " + current.getOrderNumber() + " could not be completed ("
                    + current.getFailureReason() + "). Your cart is unchanged: please place the order again.");
            default -> new PlaceResult(current.getId(), current.getStatus(), created);
        };
    }

    private record Resolved(ProductClient.ProductData product, int quantity) {}

    private List<PricedLine> price(CartClient.CartData cart) {
        List<Resolved> resolved = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (CartClient.CartData.Line line : cart.items()) {
            Optional<ProductClient.ProductData> product = downstream.product(line.productId());
            if (product.isEmpty()) {
                unavailable.add(line.name());
            } else {
                resolved.add(new Resolved(product.get(), line.quantity()));
            }
        }
        if (!unavailable.isEmpty()) {
            throw new ConflictException("These items are no longer available, please remove them from your cart: "
                    + String.join(", ", unavailable));
        }

        // One batch call. campaign-service is the only source of discounts. If it is down, checkout fails with a
        // 503 instead of charging the full price to a customer who saw a discount.
        List<CampaignClient.PriceData> quotes = downstream.prices(resolved.stream()
                .map(r -> new CampaignClient.PriceQuery.Item(r.product().id(), r.product().categoryId(),
                        money(r.product().basePrice())))
                .toList());
        Map<Long, CampaignClient.PriceData> byProduct = quotes == null ? Map.of()
                : quotes.stream().collect(Collectors.toMap(CampaignClient.PriceData::productId,
                Function.identity(), (first, second) -> first));

        List<PricedLine> lines = new ArrayList<>();
        for (Resolved r : resolved) {
            ProductClient.ProductData p = r.product();
            BigDecimal base = money(p.basePrice());
            CampaignClient.PriceData quote = byProduct.get(p.id());
            BigDecimal discount = quote == null || quote.discountAmount() == null ? BigDecimal.ZERO : quote.discountAmount();
            // Never trust a number blindly: a discount is between 0 and "everything but one cent".
            BigDecimal maxDiscount = base.subtract(new BigDecimal("0.01")).max(BigDecimal.ZERO);
            discount = discount.max(BigDecimal.ZERO).min(maxDiscount).setScale(2, RoundingMode.HALF_UP);
            lines.add(new PricedLine(p.id(), p.sku(), p.name(), base, discount, r.quantity()));
        }
        return lines;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    // ================= settle (shared by the request, retries and the reconciler) =================

    /**
     * Moves a PENDING order forward based on what the payment service says. Safe to call any number of times, from
     * several places at once: every state change is an atomic conditional update.
     */
    public void settle(Long orderId, PaymentClient.PaymentData known) {
        CustomerOrder order = state.find(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        PaymentClient.PaymentData payment = known;
        if (payment == null) {
            try {
                Optional<PaymentClient.PaymentData> found = order.getPaymentId() != null
                        ? downstream.payment(order.getPaymentId()) : downstream.paymentOfOrder(orderId);
                payment = found.orElse(null);
            } catch (RemoteUnavailableException e) {
                return;                                            // try again on the next run
            }
        }
        if (payment == null) {
            // No payment exists: the crash happened before the charge, or the request never arrived.
            if (order.getCreatedAt().isBefore(Instant.now().minus(props.giveUpAfter()))) {
                log.warn("Failing orderId={}: no payment was ever started", orderId);
                if (state.fail(orderId, OrderStatus.FAILED, "PAYMENT_NOT_STARTED", false, true)) {
                    compensate(orderId);
                }
            }
            return;
        }
        if (order.getPaymentId() == null) {
            state.recordPayment(orderId, payment.id());
        }

        switch (payment.status()) {
            case "SUCCESS" -> finishPaid(orderId);
            case "FAILED" -> {
                if (state.fail(orderId, OrderStatus.PAYMENT_FAILED, payment.failureReason(), false, true)) {
                    compensate(orderId);                           // releases the stock hold
                }
            }
            default -> log.debug("Payment of orderId={} is {}: waiting", orderId, payment.status());   // INITIATED
        }
    }

    private void finishPaid(Long orderId) {
        Downstream.CommitResult commit;
        try {
            commit = downstream.commit(orderId);
        } catch (RemoteUnavailableException e) {
            return;                                                // paid, but the commit is retried on the next run
        }
        if (commit == Downstream.CommitResult.COMMITTED) {
            if (state.confirm(orderId)) {
                compensate(orderId);                               // closes the cart
            }
        } else if (state.fail(orderId, OrderStatus.FAILED, "RESERVATION_EXPIRED", true, false)) {
            log.warn("Reservation of orderId={} expired before the payment settled: refunding", orderId);
            compensate(orderId);
        }
    }

    // ================= cancel =================

    public void cancel(Long customerId, Long orderId) {
        CustomerOrder order = state.findOwned(customerId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        switch (order.getStatus()) {
            case CANCELLED -> compensate(orderId);                 // a repeated cancel finishes any pending refund
            case CONFIRMED -> {
                if (!state.cancel(orderId, "Cancelled by the customer")) {
                    throw new ConflictException("The order changed at the same time. Please check its status and try again.");
                }
                compensate(orderId);
                Audit.record("order.cancel", Audit.Outcome.SUCCESS, "order", orderId, order.getOrderNumber());
            }
            case PENDING -> throw new ConflictException("The order is still being processed. Please try again in a moment.");
            case FAILED, PAYMENT_FAILED -> throw new ConflictException("This order was never completed, so there is nothing to cancel.");
            default -> throw new ConflictException("An order cannot be cancelled after it has been packed.");
        }
    }

    // ================= compensation =================

    /** Works through the follow-up flags of an order. Everything here is idempotent and safe to repeat. */
    public void compensate(Long orderId) {
        CustomerOrder order = state.find(orderId).orElse(null);
        if (order == null || !(order.isRefundDue() || order.isStockReturnDue() || order.isCartCheckoutDue())) {
            return;
        }

        boolean allDone = true;
        if (order.isStockReturnDue()) {
            try {
                downstream.release(orderId);
                state.clear(orderId, DueFlag.STOCK_RETURN);
            } catch (RemoteUnavailableException e) {
                allDone = false;
            }
        }
        if (order.isRefundDue() && !refund(order)) {
            allDone = false;
        }
        if (order.isCartCheckoutDue()) {
            try {
                downstream.checkoutCart(order.getCustomerId(), order.getCartId());
                state.clear(orderId, DueFlag.CART_CHECKOUT);
            } catch (RemoteUnavailableException e) {
                allDone = false;
            }
        }

        if (!allDone) {
            state.recordCompensationFailure(orderId);
            if (order.getCompensationAttempts() + 1 >= props.maxCompensationAttempts()) {
                log.error("MANUAL ACTION NEEDED: orderId={} still has unfinished follow-up work after {} attempts "
                                + "(refundDue={}, stockReturnDue={}, cartCheckoutDue={})", orderId,
                        order.getCompensationAttempts() + 1, order.isRefundDue(), order.isStockReturnDue(),
                        order.isCartCheckoutDue());
            }
        }
    }

    /** True when the refund is done or there is nothing to refund. */
    private boolean refund(CustomerOrder order) {
        Long orderId = order.getId();
        Optional<PaymentClient.PaymentData> payment;
        try {
            payment = order.getPaymentId() != null
                    ? downstream.payment(order.getPaymentId()) : downstream.paymentOfOrder(orderId);
        } catch (RemoteUnavailableException e) {
            return false;
        }
        if (payment.isEmpty()) {
            state.clear(orderId, DueFlag.REFUND);                  // nothing was ever charged
            return true;
        }
        PaymentClient.PaymentData p = payment.get();
        switch (p.status()) {
            case "FAILED", "REFUNDED" -> {
                state.clear(orderId, DueFlag.REFUND);
                return true;
            }
            case "INITIATED" -> {
                return false;                                      // the charge outcome is still unknown: wait
            }
            default -> { }
        }

        try {
            Downstream.RefundResult result = downstream.refund(p.id(), "order-" + orderId + "-refund", refundReason(order));
            switch (result.status()) {
                case "SUCCESS", "NOTHING_TO_REFUND" -> {
                    state.clear(orderId, DueFlag.REFUND);
                    return true;
                }
                case "PENDING" -> {
                    return false;                                  // the payment service settles it; the retry sees SUCCESS
                }
                default -> {
                    log.error("Refund of orderId={} was declined by the payment service ({})", orderId, result.status());
                    return false;
                }
            }
        } catch (RemoteUnavailableException e) {
            return false;
        }
    }

    private static String refundReason(CustomerOrder order) {
        return order.getStatus() == OrderStatus.CANCELLED
                ? "Order " + order.getOrderNumber() + " cancelled by the customer"
                : "Order " + order.getOrderNumber() + " could not be completed";
    }
}
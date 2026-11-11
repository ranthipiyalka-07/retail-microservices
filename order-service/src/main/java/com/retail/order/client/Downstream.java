package com.retail.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.order.exception.RemoteUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class Downstream {

    private static final Logger log = LoggerFactory.getLogger(Downstream.class);

    public record ReserveResult(boolean ok, String message) {
        public static ReserveResult success() { return new ReserveResult(true, null); }
        public static ReserveResult shortage(String message) { return new ReserveResult(false, message); }
    }

    public enum CommitResult { COMMITTED, EXPIRED }

    /** SUCCESS, PENDING, FAILED, or NOTHING_TO_REFUND. */
    public record RefundResult(String status) {}

    private final CartClient carts;
    private final InventoryClient inventory;
    private final PaymentClient payments;
    private final ProductClient products;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final ObjectMapper mapper;
    private final String internalKey;
    private final CampaignClient campaigns;

    public Downstream(CartClient carts, InventoryClient inventory, PaymentClient payments, ProductClient products,
                      CampaignClient campaigns, CircuitBreakerFactory<?, ?> breakers, ObjectMapper mapper,
                      @Value("${retail.internal.api-key}") String internalKey) {
        this.carts = carts;
        this.inventory = inventory;
        this.payments = payments;
        this.products = products;
        this.campaigns = campaigns;
        this.breakers = breakers;
        this.mapper = mapper;
        this.internalKey = internalKey;
    }

    // ---------- campaign ----------

    /** One entry per item. A failure is a RemoteUnavailableException: checkout must not guess the price. */
    public List<CampaignClient.PriceData> prices(List<CampaignClient.PriceQuery.Item> items) {
        return call("campaign", () -> campaigns.effective(internalKey, new CampaignClient.PriceQuery(items)));
    }

    // ---------- cart ----------

    public CartClient.CartData cart(Long customerId) {
        return call("cart", () -> carts.getCart(internalKey, customerId));
    }

    public void checkoutCart(Long customerId, Long cartId) {
        call("cart", () -> {
            carts.checkout(internalKey, customerId, cartId);
            return null;
        });
    }

    // ---------- product ----------

    /** Empty means "no such (active) product". */
    public Optional<ProductClient.ProductData> product(Long productId) {
        return call("product", () -> {
            try {
                return Optional.ofNullable(products.getProduct(productId));
            } catch (FeignException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    // ---------- inventory ----------

    public ReserveResult reserve(Long orderId, List<InventoryClient.ReserveRequest.Item> items) {
        return call("inventory", () -> {
            try {
                inventory.reserve(internalKey, new InventoryClient.ReserveRequest(orderId, items));
                return ReserveResult.success();
            } catch (FeignException.Conflict e) {
                return ReserveResult.shortage(messageOf(e, "Some items are out of stock"));
            }
        });
    }

    /** Idempotent. EXPIRED means the hold is gone (409 expired, or 404 no reservation). */
    public CommitResult commit(Long orderId) {
        return call("inventory", () -> {
            try {
                inventory.commit(internalKey, orderId);
                return CommitResult.COMMITTED;
            } catch (FeignException.Conflict | FeignException.NotFound e) {
                return CommitResult.EXPIRED;
            }
        });
    }

    /** Idempotent: an unknown order is not an error. */
    public void release(Long orderId) {
        call("inventory", () -> {
            inventory.release(internalKey, orderId);
            return null;
        });
    }

    // ---------- payment ----------

    /** Returns SUCCESS, FAILED or INITIATED (outcome unknown). A technical failure means the outcome is unknown too. */
    public PaymentClient.PaymentData charge(PaymentClient.ChargeRequest request) {
        return call("payment", () -> payments.charge(internalKey, request));
    }

    public Optional<PaymentClient.PaymentData> payment(Long paymentId) {
        return call("payment", () -> {
            try {
                return Optional.ofNullable(payments.get(internalKey, paymentId));
            } catch (FeignException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    public Optional<PaymentClient.PaymentData> paymentOfOrder(Long orderId) {
        return call("payment", () -> {
            List<PaymentClient.PaymentData> list = payments.byOrder(internalKey, orderId);
            return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));   // newest first
        });
    }

    public RefundResult refund(Long paymentId, String idempotencyKey, String reason) {
        return call("payment", () -> {
            try {
                PaymentClient.RefundData data = payments.refund(internalKey, paymentId,
                        new PaymentClient.RefundBody(idempotencyKey, null, reason));      // no amount = everything refundable
                return new RefundResult(data.status());
            } catch (FeignException.Conflict e) {
                return new RefundResult("NOTHING_TO_REFUND");
            }
        });
    }

    // ---------- helpers ----------

    private <T> T call(String dependency, Supplier<T> action) {
        CircuitBreaker breaker = breakers.create(dependency);
        return breaker.run(action, throwable -> {
            log.warn("Call to {} failed: {}", dependency, throwable.toString());
            throw new RemoteUnavailableException(dependency);
        });
    }

    private String messageOf(FeignException e, String fallback) {
        try {
            String text = mapper.readTree(e.contentUTF8()).path("message").asText(null);
            return text == null || text.isBlank() ? fallback : text;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
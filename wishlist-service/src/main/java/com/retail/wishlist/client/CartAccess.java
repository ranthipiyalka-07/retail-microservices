package com.retail.wishlist.client;

import com.retail.wishlist.exception.RemoteUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CartAccess {

    private static final Logger log = LoggerFactory.getLogger(CartAccess.class);

    public enum Outcome { ADDED, PRODUCT_UNAVAILABLE, LIMIT_EXCEEDED, CONFLICT }

    public record AddResult(Outcome outcome, int totalQuantity, BigDecimal subtotal) {
        static AddResult of(Outcome outcome) {
            return new AddResult(outcome, 0, BigDecimal.ZERO);
        }
    }

    private final CartClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String internalKey;

    public CartAccess(CartClient client, CircuitBreakerFactory<?, ?> breakers,
                      @Value("${retail.internal.api-key}") String internalKey) {
        this.client = client;
        this.breakers = breakers;
        this.internalKey = internalKey;
    }

    public AddResult add(Long customerId, Long productId, int quantity) {
        CircuitBreaker breaker = breakers.create("cart-service");
        return breaker.run(() -> {
            try {
                CartClient.CartData cart = client.addItem(internalKey, customerId, new CartClient.AddItem(productId, quantity));
                return new AddResult(Outcome.ADDED, cart.totalQuantity() == null ? 0 : cart.totalQuantity(),
                        cart.subtotal() == null ? BigDecimal.ZERO : cart.subtotal());
            } catch (FeignException.NotFound e) {
                return AddResult.of(Outcome.PRODUCT_UNAVAILABLE);          // the cart does not find the product (any more)
            } catch (FeignException.UnprocessableEntity e) {
                return AddResult.of(Outcome.LIMIT_EXCEEDED);                // 99 per line, 50 different products
            } catch (FeignException.Conflict e) {
                return AddResult.of(Outcome.CONFLICT);                      // a parallel change to the same cart
            }
        }, throwable -> {
            log.warn("cart-service call failed for customerId={}, productId={}: {}", customerId, productId, throwable.toString());
            throw new RemoteUnavailableException("cart");
        });
    }
}
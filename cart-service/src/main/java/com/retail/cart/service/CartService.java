package com.retail.cart.service;

import com.retail.cart.client.ProductInfo;
import com.retail.cart.client.ProductLookup;
import com.retail.cart.domain.Cart;
import com.retail.cart.domain.CartItem;
import com.retail.cart.domain.CartStatus;
import com.retail.cart.dto.CartItemResponse;
import com.retail.cart.dto.CartLimits;
import com.retail.cart.dto.CartResponse;
import com.retail.cart.exception.CartConflictException;
import com.retail.cart.exception.CartLimitExceededException;
import com.retail.cart.repository.CartItemRepository;
import com.retail.cart.repository.CartRepository;
import com.retail.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final Duration GUEST_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductLookup productLookup;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public CartService(CartRepository carts, CartItemRepository items, ProductLookup productLookup,
                       PlatformTransactionManager txManager) {
        this.carts = carts;
        this.items = items;
        this.productLookup = productLookup;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public CartResponse view(CartOwner owner) {
        return readTx.execute(status -> findActive(owner).map(this::toResponse).orElseGet(CartResponse::empty));
    }

    public CartResponse addItem(CartOwner owner, Long productId, int quantity) {
        // 1. The remote call first, outside any transaction.
        ProductInfo product = productLookup.find(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        BigDecimal price = product.currentPrice().setScale(2, RoundingMode.HALF_UP);

        // 2. Then the database work.
        return run(() -> {
            Cart cart = findActive(owner).orElseGet(() -> createCart(owner));
            CartItem item = items.findByCartIdAndProductId(cart.getId(), productId).orElse(null);

            if (item == null) {
                if (items.countByCartId(cart.getId()) >= CartLimits.MAX_DISTINCT_ITEMS) {
                    throw new CartLimitExceededException(
                            "A cart can hold at most " + CartLimits.MAX_DISTINCT_ITEMS + " different products");
                }
                item = new CartItem();
                item.setCartId(cart.getId());
                item.setProductId(productId);
                item.setQuantity(quantity);
            } else {
                int total = item.getQuantity() + quantity;
                if (total > CartLimits.MAX_QUANTITY) {
                    throw new CartLimitExceededException(
                            "You can have at most " + CartLimits.MAX_QUANTITY + " of the same product in the cart");
                }
                item.setQuantity(total);
            }
            // The snapshot is refreshed on every add. The order service checks prices again at checkout.
            item.setProductName(product.name());
            item.setUnitPrice(price);
            item.setImageUrl(product.primaryImageUrl());
            items.saveAndFlush(item);

            touch(cart);
            return toResponse(cart);
        });
    }

    public CartResponse updateQuantity(CartOwner owner, Long itemId, int quantity) {
        return run(() -> {
            Cart cart = findActive(owner).orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
            CartItem item = items.findByIdAndCartId(itemId, cart.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
            item.setQuantity(quantity);
            touch(cart);
            return toResponse(cart);
        });
    }

    public CartResponse removeItem(CartOwner owner, Long itemId) {
        return run(() -> {
            Cart cart = findActive(owner).orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
            CartItem item = items.findByIdAndCartId(itemId, cart.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
            items.delete(item);
            items.flush();
            touch(cart);
            return toResponse(cart);
        });
    }

    public void clear(CartOwner owner) {
        run(() -> {
            findActive(owner).ifPresent(cart -> {
                touch(cart);                                    // flushed before the bulk delete below
                items.deleteAllByCartId(cart.getId());
            });
            return null;
        });
    }

    /**
     * Moves the guest cart's items into the customer's cart. Safe to call twice (retries, double clicks):
     * the guest cart is claimed with one atomic update, so only the first call copies anything.
     */
    public CartResponse merge(Long customerId, String guestToken) {
        return run(() -> {
            Long guestCartId = findActive(CartOwner.guest(guestToken)).map(Cart::getId).orElse(null);
            boolean claimed = guestCartId != null && carts.claimForMerge(guestCartId);

            // Looked up AFTER the claim: the claim clears the persistence context.
            Optional<Cart> existing = carts.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE);
            if (!claimed) {
                return existing.map(this::toResponse).orElseGet(CartResponse::empty);
            }

            Cart customerCart = existing.orElseGet(() -> createCart(CartOwner.customer(customerId)));
            Map<Long, CartItem> byProduct = new HashMap<>();
            items.findByCartIdOrderByIdAsc(customerCart.getId()).forEach(i -> byProduct.put(i.getProductId(), i));
            int lines = byProduct.size();

            for (CartItem guestItem : items.findByCartIdOrderByIdAsc(guestCartId)) {
                CartItem target = byProduct.get(guestItem.getProductId());
                if (target != null) {
                    target.setQuantity(Math.min(CartLimits.MAX_QUANTITY, target.getQuantity() + guestItem.getQuantity()));
                } else if (lines < CartLimits.MAX_DISTINCT_ITEMS) {
                    CartItem copy = new CartItem();
                    copy.setCartId(customerCart.getId());
                    copy.setProductId(guestItem.getProductId());
                    copy.setProductName(guestItem.getProductName());
                    copy.setUnitPrice(guestItem.getUnitPrice());
                    copy.setQuantity(Math.min(CartLimits.MAX_QUANTITY, guestItem.getQuantity()));
                    copy.setImageUrl(guestItem.getImageUrl());
                    items.save(copy);
                    lines++;
                }
            }
            touch(customerCart);
            log.info("Guest cart merged into customer cart: customerId={}", customerId);
            return toResponse(customerCart);
        });
    }

    // ---------- helpers ----------

    /**
     * Runs the work in one transaction. A race (two requests creating the same cart or changing the same line)
     * surfaces as a duplicate-key or version error: the work is repeated once in a fresh transaction,
     * which then sees the other request's result.
     */
    private <T> T run(Supplier<T> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException first) {
            log.debug("Concurrent cart change detected, retrying once: {}", first.getClass().getSimpleName());
            try {
                return tx.execute(status -> work.get());
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException second) {
                log.warn("Cart change failed after retry: {}", second.getClass().getSimpleName());
                throw new CartConflictException();
            }
        }
    }

    private Optional<Cart> findActive(CartOwner owner) {
        if (owner.isCustomer()) {
            return carts.findByCustomerIdAndStatus(owner.customerId(), CartStatus.ACTIVE);
        }
        if (owner.guestToken() == null) {
            return Optional.empty();
        }
        return carts.findByGuestTokenAndStatus(owner.guestToken(), CartStatus.ACTIVE)
                .filter(c -> c.getExpiresAt() != null && c.getExpiresAt().isAfter(Instant.now()));
    }

    private Cart createCart(CartOwner owner) {
        Cart cart = new Cart();
        cart.setStatus(CartStatus.ACTIVE);
        if (owner.isCustomer()) {
            cart.setCustomerId(owner.customerId());
        } else {
            cart.setGuestToken(newGuestToken());               // always server-generated, never client-chosen
            cart.setExpiresAt(Instant.now().plus(GUEST_TTL));
        }
        return carts.saveAndFlush(cart);                       // flush now: a duplicate shows up inside run()
    }

    private void touch(Cart cart) {
        Instant now = Instant.now();
        cart.setUpdatedAt(now);
        if (cart.getGuestToken() != null) {
            cart.setExpiresAt(now.plus(GUEST_TTL));            // sliding expiry: activity keeps the guest cart alive
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> lines = items.findByCartIdOrderByIdAsc(cart.getId()).stream()
                .map(i -> new CartItemResponse(i.getId(), i.getProductId(), i.getProductName(), i.getUnitPrice(),
                        i.getQuantity(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())),
                        i.getImageUrl()))
                .toList();
        int totalQuantity = lines.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal subtotal = lines.stream().map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new CartResponse(cart.getId(), cart.getGuestToken(), lines, totalQuantity, subtotal);
    }

    /** 256 random bits, URL-safe, 43 characters. */
    private static String newGuestToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Used by order-service after a paid order. Only the cart that was ordered is closed; anything else is left alone. */
    public boolean markCheckedOut(Long customerId, Long cartId) {
        return run(() -> {
            Optional<Cart> cart = carts.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE);
            return cart.isPresent() && cart.get().getId().equals(cartId)
                    && carts.transition(cartId, CartStatus.ACTIVE, CartStatus.CHECKED_OUT, Instant.now()) == 1;
        });
    }
}
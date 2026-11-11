package com.retail.cart.service;

import com.retail.cart.client.ProductInfo;
import com.retail.cart.client.ProductLookup;
import com.retail.cart.domain.Cart;
import com.retail.cart.domain.CartItem;
import com.retail.cart.domain.CartStatus;
import com.retail.cart.dto.CartResponse;
import com.retail.cart.exception.CartLimitExceededException;
import com.retail.cart.repository.CartItemRepository;
import com.retail.cart.repository.CartRepository;
import com.retail.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    /** Transactions do nothing in these tests: only the cart logic is checked. */
    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock ProductLookup productLookup;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(carts, items, productLookup, new NoOpTransactionManager());
    }

    private static Cart cart(Long id, Long customerId, String token) {
        Cart c = new Cart();
        c.setId(id);
        c.setCustomerId(customerId);
        c.setGuestToken(token);
        c.setStatus(CartStatus.ACTIVE);
        return c;
    }

    private static CartItem item(Long id, Long cartId, Long productId, int quantity) {
        CartItem i = new CartItem();
        i.setId(id);
        i.setCartId(cartId);
        i.setProductId(productId);
        i.setProductName("Product " + productId);
        i.setUnitPrice(new BigDecimal("10.00"));
        i.setQuantity(quantity);
        return i;
    }

    private static ProductInfo tee() {
        return new ProductInfo(10L, "Classic Tee", new BigDecimal("19.99"), null,
                List.of(new ProductInfo.Image("https://example.com/tee.jpg", true)));
    }

    @Test
    void firstAddWithoutATokenCreatesAGuestCartWithAServerGeneratedToken() {
        when(productLookup.find(10L)).thenReturn(Optional.of(tee()));
        when(carts.saveAndFlush(any(Cart.class))).thenAnswer(inv -> {
            Cart saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(items.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.empty());
        when(items.countByCartId(1L)).thenReturn(0L);
        List<CartItem> stored = new ArrayList<>();
        when(items.saveAndFlush(any(CartItem.class))).thenAnswer(inv -> {
            CartItem saved = inv.getArgument(0);
            saved.setId(100L);
            stored.add(saved);
            return saved;
        });
        when(items.findByCartIdOrderByIdAsc(1L)).thenAnswer(inv -> List.copyOf(stored));

        CartResponse response = service.addItem(CartOwner.guest(null), 10L, 2);

        assertThat(response.guestToken()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("Classic Tee");
        assertThat(response.items().get(0).imageUrl()).isEqualTo("https://example.com/tee.jpg");
        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.subtotal()).isEqualByComparingTo("39.98");
    }

    @Test
    void addingAnUnknownProductGivesNotFoundAndTouchesNoCart() {
        when(productLookup.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(CartOwner.customer(5L), 99L, 1))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(carts, items);
    }

    @Test
    void addingTheSameProductAgainSumsTheQuantityAndRefreshesThePrice() {
        Cart cart = cart(1L, 5L, null);
        CartItem existing = item(50L, 1L, 10L, 2);
        when(productLookup.find(10L)).thenReturn(Optional.of(tee()));
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.of(existing));
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(existing));

        CartResponse response = service.addItem(CartOwner.customer(5L), 10L, 3);

        assertThat(existing.getQuantity()).isEqualTo(5);
        assertThat(existing.getUnitPrice()).isEqualByComparingTo("19.99");
        assertThat(response.totalQuantity()).isEqualTo(5);
        verify(carts, never()).saveAndFlush(any());
    }

    @Test
    void aLineCannotPassTheMaximumQuantity() {
        Cart cart = cart(1L, 5L, null);
        when(productLookup.find(10L)).thenReturn(Optional.of(tee()));
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.of(item(50L, 1L, 10L, 95)));

        assertThatThrownBy(() -> service.addItem(CartOwner.customer(5L), 10L, 10))
                .isInstanceOf(CartLimitExceededException.class);
    }

    @Test
    void aCartCannotHoldMoreThanTheMaximumNumberOfDifferentProducts() {
        Cart cart = cart(1L, 5L, null);
        when(productLookup.find(10L)).thenReturn(Optional.of(tee()));
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.empty());
        when(items.countByCartId(1L)).thenReturn(50L);

        assertThatThrownBy(() -> service.addItem(CartOwner.customer(5L), 10L, 1))
                .isInstanceOf(CartLimitExceededException.class);
    }

    @Test
    void anItemOfAnotherCartCannotBeUpdated() {
        Cart cart = cart(1L, 5L, null);
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateQuantity(CartOwner.customer(5L), 99L, 2))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anExpiredGuestCartIsTreatedAsEmpty() {
        Cart expired = cart(1L, null, "expired-token-0123456789");
        expired.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(carts.findByGuestTokenAndStatus("expired-token-0123456789", CartStatus.ACTIVE))
                .thenReturn(Optional.of(expired));

        CartResponse response = service.view(CartOwner.guest("expired-token-0123456789"));

        assertThat(response.cartId()).isNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void mergeSumsQuantitiesUpToTheLimitAndCopiesNewProducts() {
        String token = "guest-token-0123456789";
        Cart guestCart = cart(7L, null, token);
        guestCart.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        Cart customerCart = cart(3L, 5L, null);
        CartItem customerItem = item(1L, 3L, 10L, 95);

        when(carts.findByGuestTokenAndStatus(token, CartStatus.ACTIVE)).thenReturn(Optional.of(guestCart));
        when(carts.claimForMerge(7L)).thenReturn(true);
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.of(customerCart));
        when(items.findByCartIdOrderByIdAsc(3L)).thenReturn(List.of(customerItem));
        when(items.findByCartIdOrderByIdAsc(7L)).thenReturn(List.of(item(2L, 7L, 10L, 10), item(3L, 7L, 11L, 2)));

        CartResponse response = service.merge(5L, token);

        assertThat(customerItem.getQuantity()).isEqualTo(99);                       // 95 + 10, capped
        ArgumentCaptor<CartItem> copied = ArgumentCaptor.forClass(CartItem.class);
        verify(items).save(copied.capture());
        assertThat(copied.getValue().getProductId()).isEqualTo(11L);                // new product copied over
        assertThat(copied.getValue().getCartId()).isEqualTo(3L);
        assertThat(response.cartId()).isEqualTo(3L);
    }

    @Test
    void mergeWithoutAnActiveGuestCartChangesNothingAndCreatesNoCart() {
        String token = "guest-token-0123456789";
        when(carts.findByGuestTokenAndStatus(token, CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.empty());

        CartResponse response = service.merge(5L, token);

        assertThat(response.cartId()).isNull();
        verify(carts, never()).saveAndFlush(any());
        verify(carts, never()).claimForMerge(any());
    }

    @Test
    void aSecondMergeOfTheSameGuestCartDoesNotCopyAnythingAgain() {
        String token = "guest-token-0123456789";
        Cart guestCart = cart(7L, null, token);
        guestCart.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        Cart customerCart = cart(3L, 5L, null);
        when(carts.findByGuestTokenAndStatus(token, CartStatus.ACTIVE)).thenReturn(Optional.of(guestCart));
        when(carts.claimForMerge(7L)).thenReturn(false);                            // somebody else got there first
        when(carts.findByCustomerIdAndStatus(5L, CartStatus.ACTIVE)).thenReturn(Optional.of(customerCart));
        when(items.findByCartIdOrderByIdAsc(3L)).thenReturn(List.of());

        CartResponse response = service.merge(5L, token);

        assertThat(response.cartId()).isEqualTo(3L);
        verify(items, never()).save(any());
    }
}
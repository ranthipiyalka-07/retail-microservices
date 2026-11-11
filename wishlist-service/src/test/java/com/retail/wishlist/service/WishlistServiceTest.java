package com.retail.wishlist.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.wishlist.client.CartAccess;
import com.retail.wishlist.client.ProductCards;
import com.retail.wishlist.config.WishlistProperties;
import com.retail.wishlist.domain.Wishlist;
import com.retail.wishlist.domain.WishlistItem;
import com.retail.wishlist.dto.MoveToCartResponse;
import com.retail.wishlist.dto.ProductCard;
import com.retail.wishlist.dto.WishlistInfo;
import com.retail.wishlist.dto.WishlistResponse;
import com.retail.wishlist.exception.ConflictException;
import com.retail.wishlist.exception.InvalidRequestException;
import com.retail.wishlist.exception.LimitExceededException;
import com.retail.wishlist.exception.RemoteUnavailableException;
import com.retail.wishlist.repository.WishlistItemRepository;
import com.retail.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WishlistServiceTest {

    /** Transactions do nothing in these tests: only the wishlist logic is checked. */
    static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @Mock WishlistRepository wishlists;
    @Mock WishlistItemRepository items;
    @Mock ProductCards productCards;
    @Mock CartAccess cartAccess;

    private WishlistService service;

    @BeforeEach
    void setUp() {
        service = new WishlistService(wishlists, items, productCards, cartAccess,
                new WishlistProperties(3, "My Wishlist"), new NoOpTransactionManager());
        when(items.saveAndFlush(any(WishlistItem.class))).thenAnswer(returnsFirstArg());
    }

    // ---------- helpers ----------

    private static Wishlist wishlist(long id) {
        Wishlist w = new Wishlist();
        w.setId(id);
        w.setCustomerId(5L);
        w.setName("My Wishlist");
        return w;
    }

    private static WishlistItem item(long id, long wishlistId, long productId, Instant addedAt) {
        WishlistItem i = new WishlistItem();
        i.setId(id);
        i.setWishlistId(wishlistId);
        i.setProductId(productId);
        i.setAddedAt(addedAt);
        return i;
    }

    private static ProductCard card(String name) {
        return new ProductCard(name, "Northwind", new BigDecimal("19.99"), new BigDecimal("15.99"),
                new BigDecimal("4.00"), "Tee Sale", BigDecimal.ZERO, 0, null);
    }

    /** The customer already has a wishlist with id 1. */
    private void wishlistExists() {
        when(wishlists.findByCustomerId(5L)).thenReturn(Optional.of(wishlist(1L)));
        when(wishlists.existsByCustomerId(5L)).thenReturn(true);
        when(wishlists.findByCustomerIdForUpdate(5L)).thenReturn(Optional.of(wishlist(1L)));
    }

    private void productIsAvailable(long productId) {
        when(productCards.cards(List.of(productId))).thenReturn(Map.of(productId, card("Tee " + productId)));
    }

    // ---------- add ----------

    @Test
    void firstAddCreatesTheWishlistAndStoresTheItem() {
        productIsAvailable(7L);
        when(wishlists.existsByCustomerId(5L)).thenReturn(false);
        when(wishlists.saveAndFlush(any(Wishlist.class))).thenAnswer(inv -> {
            Wishlist saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(wishlists.findByCustomerIdForUpdate(5L)).thenReturn(Optional.of(wishlist(1L)));
        when(items.findByWishlistIdAndProductId(1L, 7L)).thenReturn(Optional.empty());
        when(items.countByWishlistId(1L)).thenReturn(0L);

        WishlistService.AddResult result = service.add(5L, 7L);

        assertThat(result.created()).isTrue();
        assertThat(result.item().productId()).isEqualTo(7L);
        assertThat(result.item().available()).isTrue();
        assertThat(result.item().product().name()).isEqualTo("Tee 7");
        ArgumentCaptor<Wishlist> created = ArgumentCaptor.forClass(Wishlist.class);
        verify(wishlists).saveAndFlush(created.capture());
        assertThat(created.getValue().getCustomerId()).isEqualTo(5L);
        assertThat(created.getValue().getName()).isEqualTo("My Wishlist");
        ArgumentCaptor<WishlistItem> stored = ArgumentCaptor.forClass(WishlistItem.class);
        verify(items).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getWishlistId()).isEqualTo(1L);
        assertThat(stored.getValue().getProductId()).isEqualTo(7L);
        assertThat(stored.getValue().getAddedAt()).isNotNull();
    }

    @Test
    void addingAProductThatIsAlreadyThereChangesNothing() {
        productIsAvailable(7L);
        wishlistExists();
        Instant earlier = Instant.parse("2026-11-01T10:00:00Z");
        when(items.findByWishlistIdAndProductId(1L, 7L)).thenReturn(Optional.of(item(9L, 1L, 7L, earlier)));

        WishlistService.AddResult result = service.add(5L, 7L);

        assertThat(result.created()).isFalse();
        assertThat(result.item().addedAt()).isEqualTo(earlier);
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void anUnknownOrInactiveProductCannotBeAdded() {
        when(productCards.cards(List.of(99L))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.add(5L, 99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(wishlists, items);
    }

    @Test
    void aFullWishlistRefusesNewProducts() {
        productIsAvailable(7L);
        wishlistExists();
        when(items.findByWishlistIdAndProductId(1L, 7L)).thenReturn(Optional.empty());
        when(items.countByWishlistId(1L)).thenReturn(3L);                       // the limit in these tests

        assertThatThrownBy(() -> service.add(5L, 7L)).isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("at most 3");
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void twoFirstTimeRequestsAtOnceAreRepeatedOnceAndTheLoserFindsTheWinnersWishlist() {
        productIsAvailable(7L);
        when(wishlists.existsByCustomerId(5L)).thenReturn(false, true);
        when(wishlists.saveAndFlush(any(Wishlist.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(wishlists.findByCustomerIdForUpdate(5L)).thenReturn(Optional.of(wishlist(1L)));
        when(items.findByWishlistIdAndProductId(1L, 7L)).thenReturn(Optional.empty());
        when(items.countByWishlistId(1L)).thenReturn(0L);

        WishlistService.AddResult result = service.add(5L, 7L);

        assertThat(result.created()).isTrue();
        verify(wishlists, times(1)).saveAndFlush(any());
    }

    @Test
    void aDeadlockIsRepeatedOnce() {
        productIsAvailable(7L);
        wishlistExists();
        when(items.findByWishlistIdAndProductId(1L, 7L)).thenReturn(Optional.empty());
        when(items.countByWishlistId(1L)).thenReturn(0L);
        when(items.saveAndFlush(any(WishlistItem.class)))
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenAnswer(returnsFirstArg());

        assertThat(service.add(5L, 7L).created()).isTrue();
    }

    @Test
    void aSecondFailureInARowIsAConflict() {
        productIsAvailable(7L);
        when(wishlists.existsByCustomerId(5L)).thenReturn(false);
        when(wishlists.saveAndFlush(any(Wishlist.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.add(5L, 7L)).isInstanceOf(ConflictException.class);
    }

    // ---------- remove ----------

    @Test
    void removingDeletesTheItemOfTheCustomersWishlist() {
        wishlistExists();

        service.remove(5L, 7L);

        verify(items).deleteItem(1L, 7L);
    }

    @Test
    void removingForACustomerWithoutAWishlistDoesNothing() {
        when(wishlists.findByCustomerId(5L)).thenReturn(Optional.empty());

        service.remove(5L, 7L);

        verify(items, never()).deleteItem(anyLong(), anyLong());
    }

    // ---------- view ----------

    @Test
    void viewForACustomerWithoutAWishlistIsEmptyAndCreatesNothing() {
        when(wishlists.findByCustomerId(5L)).thenReturn(Optional.empty());

        WishlistResponse response = service.view(5L, null, null);

        assertThat(response.id()).isNull();
        assertThat(response.name()).isEqualTo("My Wishlist");
        assertThat(response.items()).isEmpty();
        verify(wishlists, never()).saveAndFlush(any());
        verifyNoInteractions(productCards);
    }

    @Test
    void viewShowsLiveDetailsAndMarksMissingProductsAsUnavailable() {
        wishlistExists();
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        when(items.findByWishlistId(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(item(2L, 1L, 2L, t.plusSeconds(60)), item(1L, 1L, 1L, t)), PageRequest.of(0, 20), 2));
        when(productCards.cards(any())).thenReturn(Map.of(1L, card("Tee")));          // product 2 is gone

        WishlistResponse response = service.view(5L, null, null);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.items()).extracting(i -> i.productId()).containsExactly(2L, 1L);   // order of the page
        assertThat(response.items().get(0).available()).isFalse();
        assertThat(response.items().get(0).product()).isNull();
        assertThat(response.items().get(1).available()).isTrue();
        assertThat(response.items().get(1).product().effectivePrice()).isEqualByComparingTo("15.99");
        assertThat(response.items().get(1).product().campaignName()).isEqualTo("Tee Sale");
    }

    @Test
    void viewClampsThePageAndSortsNewestFirst() {
        wishlistExists();
        when(items.findByWishlistId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        service.view(5L, -3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(items).findByWishlistId(eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("addedAt").isDescending()).isTrue();
        verifyNoInteractions(productCards);                                            // nothing to look up
    }

    // ---------- rename ----------

    @Test
    void renameCleansTheNameAndCreatesTheWishlistWhenNeeded() {
        wishlistExists();
        when(items.countByWishlistId(1L)).thenReturn(4L);

        WishlistInfo info = service.rename(5L, "  Birthday \t  ideas ");

        assertThat(info.name()).isEqualTo("Birthday ideas");
        assertThat(info.itemCount()).isEqualTo(4L);
    }

    @Test
    void aNameWithoutVisibleCharactersIsRejected() {
        assertThatThrownBy(() -> service.rename(5L, "\u0001\u0002  ")).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(wishlists);
    }

    // ---------- move to cart ----------

    private void itemIsListed(long productId) {
        wishlistExists();
        when(items.findByWishlistIdAndProductId(1L, productId))
                .thenReturn(Optional.of(item(9L, 1L, productId, Instant.now())));
    }

    private static CartAccess.AddResult added(int totalQuantity, String subtotal) {
        return new CartAccess.AddResult(CartAccess.Outcome.ADDED, totalQuantity, new BigDecimal(subtotal));
    }

    @Test
    void movingAddsToTheCartAndRemovesFromTheWishlist() {
        itemIsListed(7L);
        when(cartAccess.add(5L, 7L, 4)).thenReturn(added(6, "95.94"));

        MoveToCartResponse response = service.moveToCart(5L, 7L, 4);

        assertThat(response.quantity()).isEqualTo(4);
        assertThat(response.cartTotalQuantity()).isEqualTo(6);
        assertThat(response.cartSubtotal()).isEqualByComparingTo("95.94");
        verify(items).deleteItem(1L, 7L);
    }

    @Test
    void withoutAQuantityOneUnitIsMoved() {
        itemIsListed(7L);
        when(cartAccess.add(5L, 7L, 1)).thenReturn(added(1, "19.99"));

        assertThat(service.moveToCart(5L, 7L, null).quantity()).isEqualTo(1);
    }

    @Test
    void aProductThatIsNotOnTheWishlistIsNotFoundAndTheCartIsNotTouched() {
        when(wishlists.findByCustomerId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveToCart(5L, 7L, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cartAccess);
    }

    @Test
    void whenTheCartRefusesTheItemStaysOnTheWishlist() {
        itemIsListed(7L);

        when(cartAccess.add(5L, 7L, 1)).thenReturn(new CartAccess.AddResult(CartAccess.Outcome.PRODUCT_UNAVAILABLE, 0, BigDecimal.ZERO));
        assertThatThrownBy(() -> service.moveToCart(5L, 7L, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer available");

        when(cartAccess.add(5L, 7L, 1)).thenReturn(new CartAccess.AddResult(CartAccess.Outcome.LIMIT_EXCEEDED, 0, BigDecimal.ZERO));
        assertThatThrownBy(() -> service.moveToCart(5L, 7L, null)).isInstanceOf(LimitExceededException.class);

        when(cartAccess.add(5L, 7L, 1)).thenReturn(new CartAccess.AddResult(CartAccess.Outcome.CONFLICT, 0, BigDecimal.ZERO));
        assertThatThrownBy(() -> service.moveToCart(5L, 7L, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("try again");

        verify(items, never()).deleteItem(anyLong(), anyLong());
    }

    @Test
    void aCartOutageLeavesTheWishlistAlone() {
        itemIsListed(7L);
        when(cartAccess.add(5L, 7L, 1)).thenThrow(new RemoteUnavailableException("cart"));

        assertThatThrownBy(() -> service.moveToCart(5L, 7L, null)).isInstanceOf(RemoteUnavailableException.class);
        verify(items, never()).deleteItem(anyLong(), anyLong());
    }

    @Test
    void aFailedRemovalAfterASuccessfulCartAddStillReportsSuccess() {
        itemIsListed(7L);
        when(cartAccess.add(5L, 7L, 1)).thenReturn(added(1, "19.99"));
        when(items.deleteItem(1L, 7L)).thenThrow(new RuntimeException("database down"));

        MoveToCartResponse response = service.moveToCart(5L, 7L, null);

        assertThat(response.cartTotalQuantity()).isEqualTo(1);                      // the cart really has it: no error
    }
}
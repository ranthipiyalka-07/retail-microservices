package com.retail.wishlist.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.wishlist.client.CartAccess;
import com.retail.wishlist.client.ProductCards;
import com.retail.wishlist.config.WishlistProperties;
import com.retail.wishlist.domain.Wishlist;
import com.retail.wishlist.domain.WishlistItem;
import com.retail.wishlist.dto.*;
import com.retail.wishlist.exception.ConflictException;
import com.retail.wishlist.exception.InvalidRequestException;
import com.retail.wishlist.exception.LimitExceededException;
import com.retail.wishlist.repository.WishlistItemRepository;
import com.retail.wishlist.repository.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);

    public record AddResult(WishlistItemResponse item, boolean created) {}

    private record Entry(Long productId, Instant addedAt) {}

    private record Snapshot(Long id, String name, Page<Entry> entries) {}

    private record Added(Instant addedAt, boolean created) {}

    private final WishlistRepository wishlists;
    private final WishlistItemRepository items;
    private final ProductCards productCards;
    private final CartAccess cartAccess;
    private final WishlistProperties props;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public WishlistService(WishlistRepository wishlists, WishlistItemRepository items, ProductCards productCards,
                           CartAccess cartAccess, WishlistProperties props, PlatformTransactionManager txManager) {
        this.wishlists = wishlists;
        this.items = items;
        this.productCards = productCards;
        this.cartAccess = cartAccess;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    // ================= view =================

    /** Reads only: a customer who never added anything gets an empty list and no row is created. */
    public WishlistResponse view(Long customerId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("addedAt"), Sort.Order.desc("id")));

        Snapshot snapshot = readTx.execute(status -> wishlists.findByCustomerId(customerId)
                .map(w -> new Snapshot(w.getId(), w.getName(), items.findByWishlistId(w.getId(), pageable)
                        .map(i -> new Entry(i.getProductId(), i.getAddedAt()))))
                .orElse(null));
        if (snapshot == null) {
            return WishlistResponse.empty(props.defaultName(), pageable.getPageNumber(), pageable.getPageSize());
        }

        // The database work is finished: the product lookup runs outside any transaction.
        List<Entry> entries = snapshot.entries().getContent();
        List<Long> ids = entries.stream().map(Entry::productId).toList();
        Map<Long, ProductCard> cards = ids.isEmpty() ? Map.of() : productCards.cards(ids);

        List<WishlistItemResponse> list = entries.stream().map(e -> {
            ProductCard card = cards.get(e.productId());
            return new WishlistItemResponse(e.productId(), e.addedAt(), card != null, card);
        }).toList();
        Page<Entry> page1 = snapshot.entries();
        return new WishlistResponse(snapshot.id(), snapshot.name(), list, page1.getNumber(), page1.getSize(),
                page1.getTotalElements(), page1.getTotalPages());
    }

    // ================= add =================

    /** Idempotent: a product that is already on the list is returned as it is, with created = false. */
    public AddResult add(Long customerId, Long productId) {
        ProductCard card = productCards.cards(List.of(productId)).get(productId);
        if (card == null) {
            throw new ResourceNotFoundException("Product", productId);
        }
        Added added = run(() -> doAdd(customerId, productId));
        return new AddResult(new WishlistItemResponse(productId, added.addedAt(), true, card), added.created());
    }

    private Added doAdd(Long customerId, Long productId) {
        Wishlist wishlist = lockOrCreate(customerId);
        Optional<WishlistItem> existing = items.findByWishlistIdAndProductId(wishlist.getId(), productId);
        if (existing.isPresent()) {
            return new Added(existing.get().getAddedAt(), false);
        }
        // The wishlist row is locked, so the count and the insert below cannot interleave with another add.
        if (items.countByWishlistId(wishlist.getId()) >= props.maxItems()) {
            throw new LimitExceededException("A wishlist can hold at most " + props.maxItems() + " products");
        }
        WishlistItem item = new WishlistItem();
        item.setWishlistId(wishlist.getId());
        item.setProductId(productId);
        item.setAddedAt(Instant.now());
        items.saveAndFlush(item);
        wishlist.setUpdatedAt(item.getAddedAt());
        return new Added(item.getAddedAt(), true);
    }

    // ================= remove =================

    /** Idempotent: removing something that is not there is not an error. */
    public void remove(Long customerId, Long productId) {
        tx.executeWithoutResult(status ->
                wishlists.findByCustomerId(customerId).ifPresent(w -> items.deleteItem(w.getId(), productId)));
    }

    // ================= rename =================

    public WishlistInfo rename(Long customerId, String name) {
        String cleaned = name.replaceAll("\\p{Cntrl}", "").trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            throw new InvalidRequestException("The name must contain visible characters");
        }
        return run(() -> {
            Wishlist wishlist = lockOrCreate(customerId);
            wishlist.setName(cleaned);
            wishlist.setUpdatedAt(Instant.now());
            return new WishlistInfo(wishlist.getId(), wishlist.getName(), items.countByWishlistId(wishlist.getId()));
        });
    }

    // ================= move to cart =================

    /**
     * Adds to the cart first, removes from the wishlist second. If the removal fails, the item is simply in both
     * places: nothing is lost. A crash between the two steps, or a double click, can add the quantity twice.
     */
    public MoveToCartResponse moveToCart(Long customerId, Long productId, Integer requestedQuantity) {
        int quantity = requestedQuantity == null ? 1 : requestedQuantity;

        boolean listed = Boolean.TRUE.equals(readTx.execute(status -> wishlists.findByCustomerId(customerId)
                .flatMap(w -> items.findByWishlistIdAndProductId(w.getId(), productId)).isPresent()));
        if (!listed) {
            throw new ResourceNotFoundException("Wishlist item", productId);
        }

        CartAccess.AddResult result = cartAccess.add(customerId, productId, quantity);   // remote call, no transaction
        switch (result.outcome()) {
            case PRODUCT_UNAVAILABLE -> throw new ConflictException(
                    "This product is no longer available. You can remove it from your wishlist.");
            case LIMIT_EXCEEDED -> throw new LimitExceededException(
                    "Your cart is full, or the limit for this product was reached");
            case CONFLICT -> throw new ConflictException("Your cart was changed at the same time. Please try again.");
            case ADDED -> { }
        }

        try {
            remove(customerId, productId);
        } catch (RuntimeException e) {
            log.warn("The item was added to the cart but could not be removed from the wishlist: customerId={}, "
                    + "productId={}: {}", customerId, productId, e.toString());
        }
        return new MoveToCartResponse(productId, quantity, result.totalQuantity(), result.subtotal());
    }

    // ================= helpers =================

    /**
     * Runs the work in one transaction. A race (two requests creating the same wishlist) shows up as a duplicate-key
     * error or a deadlock: the work is repeated once in a fresh transaction, which then sees the other request's row.
     */
    private <T> T run(Supplier<T> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException first) {
            log.debug("Concurrent wishlist change detected, retrying once: {}", first.getClass().getSimpleName());
            try {
                return tx.execute(status -> work.get());
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException second) {
                log.warn("Wishlist change failed after retry: {}", second.getClass().getSimpleName());
                throw new ConflictException("Your wishlist was changed at the same time by another request. Please try again.");
            }
        }
    }

    /**
     * A plain existence check first, then a locking read of the row. Locking a row that does not exist yet would take a
     * gap lock, and two first-time requests could then deadlock.
     */
    private Wishlist lockOrCreate(Long customerId) {
        if (!wishlists.existsByCustomerId(customerId)) {
            Wishlist created = new Wishlist();
            created.setCustomerId(customerId);
            created.setName(props.defaultName());
            Instant now = Instant.now();
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            wishlists.saveAndFlush(created);
        }
        return wishlists.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalStateException("Wishlist of customer " + customerId + " disappeared"));
    }
}
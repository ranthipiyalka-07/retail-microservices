package com.retail.inventory.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.inventory.config.InventoryProperties;
import com.retail.inventory.domain.InventoryAudit;
import com.retail.inventory.domain.InventoryItem;
import com.retail.inventory.dto.AuditResponse;
import com.retail.inventory.dto.PageResponse;
import com.retail.inventory.dto.SetStockRequest;
import com.retail.inventory.dto.StockResponse;
import com.retail.inventory.exception.ConflictException;
import com.retail.inventory.exception.InvalidRequestException;
import com.retail.inventory.repository.InventoryAuditRepository;
import com.retail.inventory.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository items;
    private final InventoryAuditRepository audits;
    private final LowStockNotifier lowStock;
    private final InventoryProperties props;

    public InventoryService(InventoryItemRepository items, InventoryAuditRepository audits,
                            LowStockNotifier lowStock, InventoryProperties props) {
        this.items = items;
        this.audits = audits;
        this.lowStock = lowStock;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockResponse> list(boolean lowStockOnly, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(pageNumber(page), pageSize(size), Sort.by("productId"));
        Page<InventoryItem> result = lowStockOnly ? items.findLowStock(pageable) : items.findAll(pageable);
        return PageResponse.of(result.map(InventoryService::toResponse));
    }

    @Transactional(readOnly = true)
    public StockResponse get(Long productId) {
        return toResponse(items.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock record for product", productId)));
    }

    /**
     * Sets the quantity on hand (creating the stock record the first time).
     * The row is locked while it is changed, so a parallel reservation waits instead of seeing half a change.
     */
    @Transactional
    public StockResponse setStock(Long productId, SetStockRequest request, String actor) {
        if (productId == null || productId <= 0) {
            throw new InvalidRequestException("productId must be a positive number");
        }
        int newQuantity = request.quantity();
        InventoryItem item = items.findForUpdate(productId).orElse(null);
        boolean created = item == null;
        int previousOnHand;
        int previousAvailable;

        if (created) {
            item = new InventoryItem();
            item.setProductId(productId);
            item.setQuantityOnHand(newQuantity);
            item.setQuantityReserved(0);
            item.setReorderLevel(request.reorderLevel() != null ? request.reorderLevel() : props.defaultReorderLevel());
            previousOnHand = 0;
            previousAvailable = Integer.MAX_VALUE;                 // a brand-new record counts as "was fine before"
            try {
                items.saveAndFlush(item);
            } catch (DataIntegrityViolationException e) {
                throw new ConflictException("The stock record for product " + productId
                        + " was created at the same time by someone else. Please retry.");
            }
        } else {
            if (newQuantity < item.getQuantityReserved()) {
                throw new ConflictException("Cannot set the stock to " + newQuantity + ": "
                        + item.getQuantityReserved() + " units are reserved for open orders");
            }
            previousOnHand = item.getQuantityOnHand();
            previousAvailable = item.available();
            item.setQuantityOnHand(newQuantity);
            if (request.reorderLevel() != null) {
                item.setReorderLevel(request.reorderLevel());
            }
            items.saveAndFlush(item);
        }

        if (created || previousOnHand != newQuantity) {
            audits.save(new InventoryAudit(productId, previousOnHand, newQuantity, request.reason().trim(), actor));
        }
        lowStock.notifyIfCrossed(item, previousAvailable);
        log.info("Stock set: productId={}, {} -> {}, by={}", productId, previousOnHand, newQuantity, actor);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditResponse> history(Long productId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(pageNumber(page), pageSize(size));
        return PageResponse.of(audits.findByProductIdOrderByIdDesc(productId, pageable)
                .map(a -> new AuditResponse(a.getId(), a.getProductId(), a.getOldQuantity(), a.getNewQuantity(),
                        a.getReason(), a.getChangedBy(), a.getChangedAt())));
    }

    private static int pageNumber(Integer page) {
        return page == null ? 0 : Math.max(page, 0);
    }

    private static int pageSize(Integer size) {
        return size == null ? 20 : Math.min(Math.max(size, 1), 100);
    }

    static StockResponse toResponse(InventoryItem i) {
        return new StockResponse(i.getProductId(), i.getQuantityOnHand(), i.getQuantityReserved(),
                i.available(), i.getReorderLevel(), i.lowStock(), i.getUpdatedAt());
    }
}
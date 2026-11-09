package com.retail.inventory.web;

import com.retail.inventory.dto.AuditResponse;
import com.retail.inventory.dto.PageResponse;
import com.retail.inventory.dto.SetStockRequest;
import com.retail.inventory.dto.StockResponse;
import com.retail.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class StockController {

    private final InventoryService inventoryService;

    public StockController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public PageResponse<StockResponse> list(@RequestParam(defaultValue = "false") boolean lowStock,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return inventoryService.list(lowStock, page, size);
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable Long productId) {
        return inventoryService.get(productId);
    }

    @PutMapping("/{productId}")
    public StockResponse setStock(@PathVariable Long productId, @Valid @RequestBody SetStockRequest request,
                                  Authentication authentication) {
        return inventoryService.setStock(productId, request, authentication.getName());   // the token's subject (email)
    }

    @GetMapping("/{productId}/history")
    public PageResponse<AuditResponse> history(@PathVariable Long productId,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return inventoryService.history(productId, page, size);
    }
}
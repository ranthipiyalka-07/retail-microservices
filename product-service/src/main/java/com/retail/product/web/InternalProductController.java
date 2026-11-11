package com.retail.product.web;

import com.retail.product.dto.ProductSummary;
import com.retail.product.dto.RatingUpdateRequest;
import com.retail.product.service.PricedCatalogService;
import com.retail.product.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final RatingService ratingService;
    private final PricedCatalogService catalog;

    public InternalProductController(RatingService ratingService, PricedCatalogService catalog) {
        this.ratingService = ratingService;
        this.catalog = catalog;
    }

    @PutMapping("/{productId}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRating(@PathVariable Long productId, @Valid @RequestBody RatingUpdateRequest request) {
        ratingService.update(productId, request);
    }

    /** ?ids=1&ids=2 or ?ids=1,2. At most 100. Products that are not ACTIVE are left out of the answer. */
    @GetMapping("/summaries")
    public List<ProductSummary> summaries(@RequestParam("ids") List<Long> ids) {
        return catalog.summaries(ids);
    }
}
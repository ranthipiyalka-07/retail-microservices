package com.retail.product.web;

import com.retail.product.dto.RatingUpdateRequest;
import com.retail.product.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final RatingService ratingService;

    public InternalProductController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PutMapping("/{productId}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRating(@PathVariable Long productId, @Valid @RequestBody RatingUpdateRequest request) {
        ratingService.update(productId, request);
    }
}
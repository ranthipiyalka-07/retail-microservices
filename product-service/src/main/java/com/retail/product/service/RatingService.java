package com.retail.product.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.product.dto.RatingUpdateRequest;
import com.retail.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;

@Service
public class RatingService {

    private final ProductRepository products;

    public RatingService(ProductRepository products) {
        this.products = products;
    }

    /**
     * Idempotent. Zero changed rows for a product that exists means the stored rating is already as new
     * or newer, which is fine: a late or repeated push does nothing.
     */
    @Transactional
    public void update(Long productId, RatingUpdateRequest request) {
        int changed = products.updateRating(productId, request.averageRating().setScale(2, RoundingMode.HALF_UP),
                request.reviewCount(), request.version(), Instant.now());
        if (changed == 0 && !products.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
    }
}
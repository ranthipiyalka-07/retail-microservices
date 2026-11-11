package com.retail.cart.client;

import java.math.BigDecimal;
import java.util.List;

/** The part of product-service's response that the cart needs. Unknown JSON fields are ignored. */
public record ProductInfo(Long id, String name, BigDecimal basePrice, BigDecimal effectivePrice, List<Image> images) {

    public record Image(String url, boolean primary) {}

    /** The campaign price when there is one, the base price otherwise. */
    public BigDecimal currentPrice() {
        return effectivePrice != null ? effectivePrice : basePrice;
    }

    public String primaryImageUrl() {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(Image::primary).findFirst().orElse(images.get(0)).url();
    }
}
package com.retail.wishlist.dto;

import java.math.BigDecimal;

/** The live product details shown on the wishlist. effectivePrice is the campaign price when there is one. */
public record ProductCard(String name, String brand, BigDecimal basePrice, BigDecimal effectivePrice,
                          BigDecimal discountAmount, String campaignName, BigDecimal avgRating, int reviewCount,
                          String imageUrl) {}
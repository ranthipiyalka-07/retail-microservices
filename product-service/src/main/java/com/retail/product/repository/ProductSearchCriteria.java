package com.retail.product.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Already validated search input. Null means "no filter". */
public record ProductSearchCriteria(
        List<String> tokens,
        Set<Long> categoryIds,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating) {}
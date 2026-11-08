package com.retail.product.repository;

import com.retail.product.domain.Product;
import com.retail.product.domain.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {
    private ProductSpecifications() {}

    /** Customers only ever see ACTIVE products. */
    public static Specification<Product> from(ProductSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));

            // Every word must appear in the name, description, brand or SKU.
            for (String token : c.tokens()) {
                String pattern = "%" + escapeLike(token.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.get("brand")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sku")), pattern, '\\')));
            }
            if (c.categoryIds() != null && !c.categoryIds().isEmpty()) {
                predicates.add(root.get("categoryId").in(c.categoryIds()));
            }
            if (c.brand() != null) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), c.brand().toLowerCase(Locale.ROOT)));
            }
            if (c.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("basePrice"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("basePrice"), c.maxPrice()));
            }
            if (c.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("avgRating"), c.minRating()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** A user typing % or _ must not act as a wildcard. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
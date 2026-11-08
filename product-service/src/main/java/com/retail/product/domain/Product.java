package com.retail.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter @Setter
public class Product {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String brand;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version;

    // @BatchSize: a page of 20 products loads all images with 1 extra query, not 20.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @BatchSize(size = 50)
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<ProductAttribute> attributes = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
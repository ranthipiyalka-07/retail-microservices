package com.retail.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter @Setter
public class ProductImage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage;
}
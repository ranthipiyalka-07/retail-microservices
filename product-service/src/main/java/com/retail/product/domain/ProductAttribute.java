package com.retail.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_attributes")
@Getter @Setter
public class ProductAttribute {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "attr_name", nullable = false, length = 100)
    private String name;

    @Column(name = "attr_value", nullable = false)
    private String value;
}
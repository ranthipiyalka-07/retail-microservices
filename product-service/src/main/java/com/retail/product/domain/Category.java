package com.retail.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "categories")
@Getter @Setter
public class Category {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;                     // plain column: the tree is small and handled in the service

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
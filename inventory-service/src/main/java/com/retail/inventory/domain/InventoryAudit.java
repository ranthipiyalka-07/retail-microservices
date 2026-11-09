package com.retail.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory_audit")
@Getter @NoArgsConstructor
public class InventoryAudit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "old_quantity", nullable = false)
    private int oldQuantity;

    @Column(name = "new_quantity", nullable = false)
    private int newQuantity;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public InventoryAudit(Long productId, int oldQuantity, int newQuantity, String reason, String changedBy) {
        this.productId = productId;
        this.oldQuantity = oldQuantity;
        this.newQuantity = newQuantity;
        this.reason = reason;
        this.changedBy = changedBy;
    }

    @PrePersist
    void onCreate() { changedAt = Instant.now(); }
}
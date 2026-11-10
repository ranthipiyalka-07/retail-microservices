package com.retail.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "order_status_history")
@Getter @NoArgsConstructor
public class OrderStatusHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 25)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 25)
    private OrderStatus toStatus;

    private String comment;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public OrderStatusHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus, String comment, String changedBy) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment == null ? null : comment.substring(0, Math.min(comment.length(), 255));
        this.changedBy = changedBy;
    }

    @PrePersist
    void onCreate() { changedAt = Instant.now(); }
}
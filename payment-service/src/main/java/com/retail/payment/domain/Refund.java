package com.retail.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds")
@Getter @Setter
public class Refund {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "gateway_reference", length = 128)
    private String gatewayReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
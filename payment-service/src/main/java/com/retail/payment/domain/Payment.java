package com.retail.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** There is deliberately no column for a card number or a payment token. */
@Entity
@Table(name = "payments")
@Getter @Setter
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "gateway_reference", length = 128)
    private String gatewayReference;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

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
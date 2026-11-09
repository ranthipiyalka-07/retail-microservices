package com.retail.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** One row per settled gateway interaction. Never updated, never deleted. */
@Entity
@Table(name = "payment_transactions")
@Getter @NoArgsConstructor
public class PaymentTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "refund_id")
    private Long refundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Column(name = "gateway_response_code", length = 64)
    private String gatewayResponseCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentTransaction(Long paymentId, Long refundId, TransactionType type, BigDecimal amount,
                              TransactionStatus status, String gatewayResponseCode) {
        this.paymentId = paymentId;
        this.refundId = refundId;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.gatewayResponseCode = gatewayResponseCode == null ? null
                : gatewayResponseCode.substring(0, Math.min(gatewayResponseCode.length(), 64));
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
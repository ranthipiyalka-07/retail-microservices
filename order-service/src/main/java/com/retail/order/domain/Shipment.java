package com.retail.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shipments")
@Getter @Setter @NoArgsConstructor
public class Shipment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, length = 100)
    private String carrier;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Column(name = "shipped_at", nullable = false)
    private Instant shippedAt;

    @Column(name = "estimated_delivery")
    private Instant estimatedDelivery;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public Shipment(Long orderId, String carrier, String trackingNumber, String trackingUrl,
                    Instant shippedAt, Instant estimatedDelivery) {
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.trackingUrl = trackingUrl;
        this.shippedAt = shippedAt;
        this.estimatedDelivery = estimatedDelivery;
    }
}
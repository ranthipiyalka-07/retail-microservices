package com.retail.support.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** updatedAt is set explicitly by the services: it means "last activity the customer can see". */
@Entity
@Table(name = "support_tickets")
@Getter @Setter
public class SupportTicket {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, updatable = false, length = 32)
    private String ticketNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "customer_email", nullable = false, updatable = false)
    private String customerEmail;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "order_verified", nullable = false, updatable = false)
    private boolean orderVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TicketCategory category;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "assigned_to_email")
    private String assignedToEmail;

    @Column(name = "idempotency_key", updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
}
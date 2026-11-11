package com.retail.support.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ticket_messages")
@Getter @NoArgsConstructor
public class TicketMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, updatable = false, length = 10)
    private SenderType senderType;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(name = "author_email", nullable = false, updatable = false)
    private String authorEmail;

    @Column(name = "internal_note", nullable = false, updatable = false)
    private boolean internalNote;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TicketMessage(Long ticketId, SenderType senderType, Long senderId, String authorEmail,
                         boolean internalNote, String message) {
        this.ticketId = ticketId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.authorEmail = authorEmail;
        this.internalNote = internalNote;
        this.message = message;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
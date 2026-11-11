CREATE TABLE support_tickets (
                                 id                BIGINT       NOT NULL AUTO_INCREMENT,
                                 ticket_number     VARCHAR(32)  NOT NULL,
                                 customer_id       BIGINT       NOT NULL,
                                 customer_email    VARCHAR(255) NOT NULL,
                                 order_id          BIGINT       NULL,                -- a plain id: the order lives in order-service
                                 order_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
                                 category          VARCHAR(20)  NOT NULL,
                                 subject           VARCHAR(200) NOT NULL,
                                 description       TEXT         NOT NULL,
                                 priority          VARCHAR(10)  NOT NULL,
                                 status            VARCHAR(20)  NOT NULL,
                                 assigned_to       BIGINT       NULL,                -- the agent's user id (auth-service)
                                 assigned_to_email VARCHAR(255) NULL,
                                 idempotency_key   VARCHAR(64)  NULL,
                                 resolved_at       DATETIME(6)  NULL,
                                 closed_at         DATETIME(6)  NULL,
                                 created_at        DATETIME(6)  NOT NULL,
                                 updated_at        DATETIME(6)  NOT NULL,
                                 PRIMARY KEY (id),
                                 CONSTRAINT uk_tickets_number       UNIQUE (ticket_number),
    -- NULL keys do not collide, so a key is optional
                                 CONSTRAINT uk_tickets_customer_key UNIQUE (customer_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tickets_customer ON support_tickets (customer_id, id);
CREATE INDEX idx_tickets_queue    ON support_tickets (status, id);
CREATE INDEX idx_tickets_assignee ON support_tickets (assigned_to, status);
CREATE INDEX idx_tickets_resolved ON support_tickets (status, resolved_at);

CREATE TABLE ticket_messages (
                                 id            BIGINT       NOT NULL AUTO_INCREMENT,
                                 ticket_id     BIGINT       NOT NULL,
                                 sender_type   VARCHAR(10)  NOT NULL,
                                 sender_id     BIGINT       NOT NULL,                -- customer id or agent user id
                                 author_email  VARCHAR(255) NOT NULL,                -- shown to staff only
                                 internal_note BOOLEAN      NOT NULL DEFAULT FALSE,  -- internal notes are never shown to customers
                                 message       TEXT         NOT NULL,
                                 created_at    DATETIME(6)  NOT NULL,
                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_messages_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_messages_ticket ON ticket_messages (ticket_id, id);
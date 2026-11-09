CREATE TABLE payments (
                          id                BIGINT        NOT NULL AUTO_INCREMENT,
                          order_id          BIGINT        NOT NULL,
                          customer_id       BIGINT        NOT NULL,
                          amount            DECIMAL(12,2) NOT NULL,
                          currency          CHAR(3)       NOT NULL,
                          method            VARCHAR(20)   NOT NULL,
                          status            VARCHAR(25)   NOT NULL,
                          gateway_reference VARCHAR(128)  NULL,
                          card_last4        CHAR(4)       NULL,        -- the only card data we keep, and it comes from the gateway
                          idempotency_key   VARCHAR(64)   NOT NULL,
                          failure_reason    VARCHAR(255)  NULL,
                          created_at        DATETIME(6)   NOT NULL,
                          updated_at        DATETIME(6)   NOT NULL,
    -- An order can have only one payment that is in progress, paid, or partly refunded. Failed and fully refunded
    -- payments get NULL here and so allow a new attempt. NULLs do not collide in a unique index.
                          active_order_id   BIGINT GENERATED ALWAYS AS
                              (CASE WHEN status IN ('INITIATED','SUCCESS','PARTIALLY_REFUNDED') THEN order_id ELSE NULL END) STORED,
                          PRIMARY KEY (id),
                          CONSTRAINT uk_payments_idempotency   UNIQUE (idempotency_key),
                          CONSTRAINT uk_payments_active_order  UNIQUE (active_order_id),
                          CONSTRAINT ck_payments_amount        CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payments_order        ON payments (order_id);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);

CREATE TABLE refunds (
                         id                BIGINT        NOT NULL AUTO_INCREMENT,
                         payment_id        BIGINT        NOT NULL,
                         amount            DECIMAL(12,2) NOT NULL,
                         reason            VARCHAR(200)  NOT NULL,
                         status            VARCHAR(20)   NOT NULL,
                         gateway_reference VARCHAR(128)  NULL,
                         idempotency_key   VARCHAR(64)   NOT NULL,
                         failure_reason    VARCHAR(255)  NULL,
                         created_at        DATETIME(6)   NOT NULL,
                         updated_at        DATETIME(6)   NOT NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT uk_refunds_idempotency UNIQUE (idempotency_key),
                         CONSTRAINT ck_refunds_amount      CHECK (amount > 0),
                         CONSTRAINT fk_refunds_payment     FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_refunds_payment        ON refunds (payment_id);
CREATE INDEX idx_refunds_status_created ON refunds (status, created_at);

-- Full ledger: one row per settled gateway interaction. Rows are only ever added.
CREATE TABLE payment_transactions (
                                      id                    BIGINT        NOT NULL AUTO_INCREMENT,
                                      payment_id            BIGINT        NOT NULL,
                                      refund_id             BIGINT        NULL,
                                      type                  VARCHAR(10)   NOT NULL,
                                      amount                DECIMAL(12,2) NOT NULL,
                                      status                VARCHAR(10)   NOT NULL,
                                      gateway_response_code VARCHAR(64)   NULL,
                                      created_at            DATETIME(6)   NOT NULL,
                                      PRIMARY KEY (id),
                                      CONSTRAINT fk_tx_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
                                      CONSTRAINT fk_tx_refund  FOREIGN KEY (refund_id)  REFERENCES refunds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tx_payment ON payment_transactions (payment_id);
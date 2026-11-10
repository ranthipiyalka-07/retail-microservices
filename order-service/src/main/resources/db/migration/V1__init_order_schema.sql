CREATE TABLE orders (
                        id                    BIGINT        NOT NULL AUTO_INCREMENT,
                        order_number          VARCHAR(32)   NOT NULL,
                        customer_id           BIGINT        NOT NULL,
                        customer_email        VARCHAR(255)  NOT NULL,
                        cart_id               BIGINT        NOT NULL,
                        status                VARCHAR(25)   NOT NULL,
                        subtotal              DECIMAL(12,2) NOT NULL,
                        discount_total        DECIMAL(12,2) NOT NULL DEFAULT 0,
                        tax_total             DECIMAL(12,2) NOT NULL DEFAULT 0,
                        shipping_fee          DECIMAL(12,2) NOT NULL DEFAULT 0,
                        grand_total           DECIMAL(12,2) NOT NULL,
                        currency              CHAR(3)       NOT NULL,
                        payment_id            BIGINT        NULL,
                        idempotency_key       VARCHAR(64)   NOT NULL,
                        failure_reason        VARCHAR(255)  NULL,
    -- Follow-up work that must happen even after a crash. A reconciler retries until the flag is cleared.
                        refund_due            BOOLEAN       NOT NULL DEFAULT FALSE,
                        stock_return_due      BOOLEAN       NOT NULL DEFAULT FALSE,
                        cart_checkout_due     BOOLEAN       NOT NULL DEFAULT FALSE,
                        compensation_attempts INT           NOT NULL DEFAULT 0,
                        placed_at             DATETIME(6)   NOT NULL,
                        created_at            DATETIME(6)   NOT NULL,
                        updated_at            DATETIME(6)   NOT NULL,
    -- A cart can back only one live order. Failed attempts free the cart again, so the customer can retry.
                        active_cart_id        BIGINT GENERATED ALWAYS AS
                            (CASE WHEN status IN ('FAILED','PAYMENT_FAILED') THEN NULL ELSE cart_id END) STORED,
                        PRIMARY KEY (id),
                        CONSTRAINT uk_orders_number        UNIQUE (order_number),
                        CONSTRAINT uk_orders_customer_key  UNIQUE (customer_id, idempotency_key),
                        CONSTRAINT uk_orders_active_cart   UNIQUE (active_cart_id),
                        CONSTRAINT ck_orders_total         CHECK (grand_total > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_orders_customer       ON orders (customer_id, id);
CREATE INDEX idx_orders_status_updated ON orders (status, updated_at);

CREATE TABLE order_items (
                             id              BIGINT        NOT NULL AUTO_INCREMENT,
                             order_id        BIGINT        NOT NULL,
                             product_id      BIGINT        NOT NULL,
                             sku             VARCHAR(64)   NOT NULL,
                             product_name    VARCHAR(255)  NOT NULL,
                             unit_price      DECIMAL(12,2) NOT NULL,
                             discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
                             quantity        INT           NOT NULL,
                             line_total      DECIMAL(12,2) NOT NULL,
                             PRIMARY KEY (id),
                             CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
                             CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_addresses (
                                 id          BIGINT       NOT NULL AUTO_INCREMENT,
                                 order_id    BIGINT       NOT NULL,
                                 type        VARCHAR(20)  NOT NULL,
                                 name        VARCHAR(150) NOT NULL,
                                 line1       VARBINARY(512) NOT NULL,              -- AES-256 encrypted
                                 line2       VARBINARY(512) NULL,                  -- AES-256 encrypted
                                 city        VARCHAR(100) NOT NULL,
                                 state       VARCHAR(100) NULL,
                                 postal_code VARCHAR(20)  NOT NULL,
                                 country     CHAR(2)      NOT NULL,
                                 phone       VARBINARY(255) NULL,                  -- AES-256 encrypted
                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_order_addresses_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_addresses_order ON order_addresses (order_id);

CREATE TABLE order_status_history (
                                      id          BIGINT       NOT NULL AUTO_INCREMENT,
                                      order_id    BIGINT       NOT NULL,
                                      from_status VARCHAR(25)  NULL,
                                      to_status   VARCHAR(25)  NOT NULL,
                                      comment     VARCHAR(255) NULL,
                                      changed_by  VARCHAR(255) NOT NULL,
                                      changed_at  DATETIME(6)  NOT NULL,
                                      PRIMARY KEY (id),
                                      CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_history_order ON order_status_history (order_id, id);

CREATE TABLE shipments (
                           id                 BIGINT       NOT NULL AUTO_INCREMENT,
                           order_id           BIGINT       NOT NULL,
                           carrier            VARCHAR(100) NOT NULL,
                           tracking_number    VARCHAR(100) NOT NULL,
                           tracking_url       VARCHAR(500) NULL,
                           shipped_at         DATETIME(6)  NOT NULL,
                           estimated_delivery DATETIME(6)  NULL,
                           delivered_at       DATETIME(6)  NULL,
                           PRIMARY KEY (id),
                           CONSTRAINT uk_shipments_order UNIQUE (order_id),
                           CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
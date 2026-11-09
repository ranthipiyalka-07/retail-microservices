CREATE TABLE inventory_items (
                                 id                BIGINT      NOT NULL AUTO_INCREMENT,
                                 product_id        BIGINT      NOT NULL,
                                 quantity_on_hand  INT         NOT NULL,
                                 quantity_reserved INT         NOT NULL DEFAULT 0,
                                 reorder_level     INT         NOT NULL DEFAULT 5,
                                 created_at        DATETIME(6) NOT NULL,
                                 updated_at        DATETIME(6) NOT NULL,
                                 PRIMARY KEY (id),
                                 CONSTRAINT uk_inventory_product UNIQUE (product_id),
                                 CONSTRAINT ck_inventory_on_hand  CHECK (quantity_on_hand >= 0),
                                 CONSTRAINT ck_inventory_reserved CHECK (quantity_reserved >= 0),
    -- Safety net: reserved stock can never exceed what is on the shelf
                                 CONSTRAINT ck_inventory_reserved_le_on_hand CHECK (quantity_reserved <= quantity_on_hand),
                                 CONSTRAINT ck_inventory_reorder  CHECK (reorder_level >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_reservations (
                                    id         BIGINT      NOT NULL AUTO_INCREMENT,
                                    order_id   BIGINT      NOT NULL,
                                    product_id BIGINT      NOT NULL,
                                    quantity   INT         NOT NULL,
                                    status     VARCHAR(20) NOT NULL,
                                    expires_at DATETIME(6) NOT NULL,
                                    created_at DATETIME(6) NOT NULL,
                                    updated_at DATETIME(6) NOT NULL,
                                    PRIMARY KEY (id),
                                    CONSTRAINT uk_reservation_order_product UNIQUE (order_id, product_id),   -- also makes a retry of "reserve" idempotent
                                    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_reservation_status_expiry ON stock_reservations (status, expires_at);

CREATE TABLE inventory_audit (
                                 id           BIGINT       NOT NULL AUTO_INCREMENT,
                                 product_id   BIGINT       NOT NULL,
                                 old_quantity INT          NOT NULL,
                                 new_quantity INT          NOT NULL,
                                 reason       VARCHAR(255) NOT NULL,
                                 changed_by   VARCHAR(255) NOT NULL,
                                 changed_at   DATETIME(6)  NOT NULL,
                                 PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_product ON inventory_audit (product_id, id);
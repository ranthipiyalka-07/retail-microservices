CREATE TABLE carts (
                       id          BIGINT      NOT NULL AUTO_INCREMENT,
                       customer_id BIGINT      NULL,
                       guest_token VARCHAR(64) NULL,
                       status      VARCHAR(20) NOT NULL,
                       expires_at  DATETIME(6) NULL,
                       created_at  DATETIME(6) NOT NULL,
                       updated_at  DATETIME(6) NOT NULL,
    -- Only ACTIVE carts have a value here, so a customer can never have two ACTIVE carts (even in a race),
    -- while old CHECKED_OUT / MERGED carts stay as history. NULLs do not collide in a unique index.
                       active_customer_id BIGINT GENERATED ALWAYS AS
                           (CASE WHEN status = 'ACTIVE' THEN customer_id ELSE NULL END) STORED,
                       PRIMARY KEY (id),
                       CONSTRAINT uk_carts_guest_token     UNIQUE (guest_token),
                       CONSTRAINT uk_carts_active_customer UNIQUE (active_customer_id),
                       CONSTRAINT ck_carts_owner CHECK (
                           (customer_id IS NOT NULL AND guest_token IS NULL) OR (customer_id IS NULL AND guest_token IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_carts_customer ON carts (customer_id);
CREATE INDEX idx_carts_expires  ON carts (expires_at);

CREATE TABLE cart_items (
                            id           BIGINT        NOT NULL AUTO_INCREMENT,
                            cart_id      BIGINT        NOT NULL,
                            product_id   BIGINT        NOT NULL,
                            product_name VARCHAR(255)  NOT NULL,
                            unit_price   DECIMAL(12,2) NOT NULL,
                            quantity     INT           NOT NULL,
                            image_url    VARCHAR(500)  NULL,
                            created_at   DATETIME(6)   NOT NULL,
                            updated_at   DATETIME(6)   NOT NULL,
                            version      INT           NOT NULL DEFAULT 0,
                            PRIMARY KEY (id),
                            CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
                            CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
                            CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
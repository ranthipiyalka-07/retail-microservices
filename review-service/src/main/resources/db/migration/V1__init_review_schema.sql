CREATE TABLE reviews (
                         id           BIGINT       NOT NULL AUTO_INCREMENT,
                         product_id   BIGINT       NOT NULL,
                         customer_id  BIGINT       NOT NULL,
                         order_id     BIGINT       NOT NULL,                 -- proof of purchase
                         rating       TINYINT      NOT NULL,
                         title        VARCHAR(150) NULL,
                         comment      TEXT         NULL,
                         display_name VARCHAR(50)  NOT NULL,
                         status       VARCHAR(20)  NOT NULL,
                         created_at   DATETIME(6)  NOT NULL,
                         updated_at   DATETIME(6)  NOT NULL,
    -- One live review per purchase. A deleted review frees the slot, so the customer can write a new one.
    -- NULLs do not collide in a unique index.
                         active_slot  VARCHAR(70) GENERATED ALWAYS AS
                             (CASE WHEN status <> 'DELETED' THEN CONCAT(customer_id, ':', product_id, ':', order_id) ELSE NULL END) STORED,
                         PRIMARY KEY (id),
                         CONSTRAINT uk_reviews_active_slot UNIQUE (active_slot),
                         CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_reviews_product_status ON reviews (product_id, status, id);
CREATE INDEX idx_reviews_customer       ON reviews (customer_id, product_id);

-- One row per product. Changed only with atomic increments, in the same transaction as the review change.
CREATE TABLE product_rating_summary (
                                        product_id     BIGINT      NOT NULL,
                                        review_count   INT         NOT NULL DEFAULT 0,
                                        rating_sum     BIGINT      NOT NULL DEFAULT 0,
                                        change_version BIGINT      NOT NULL DEFAULT 0,       -- +1 on every change
                                        synced_version BIGINT      NOT NULL DEFAULT 0,       -- the version product-service has acknowledged
                                        updated_at     DATETIME(6) NOT NULL,
                                        PRIMARY KEY (product_id),
                                        CONSTRAINT ck_summary_count CHECK (review_count >= 0),
                                        CONSTRAINT ck_summary_sum   CHECK (rating_sum >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
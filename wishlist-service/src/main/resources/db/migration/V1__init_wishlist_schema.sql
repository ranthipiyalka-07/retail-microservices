CREATE TABLE wishlists (
                           id          BIGINT       NOT NULL AUTO_INCREMENT,
                           customer_id BIGINT       NOT NULL,
                           name        VARCHAR(100) NOT NULL,
                           created_at  DATETIME(6)  NOT NULL,
                           updated_at  DATETIME(6)  NOT NULL,
                           PRIMARY KEY (id),
                           CONSTRAINT uk_wishlists_customer UNIQUE (customer_id)          -- one wishlist per customer in v1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wishlist_items (
                                id          BIGINT      NOT NULL AUTO_INCREMENT,
                                wishlist_id BIGINT      NOT NULL,
                                product_id  BIGINT      NOT NULL,                              -- a plain id: the product lives in product-service
                                added_at    DATETIME(6) NOT NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT uk_wishlist_item UNIQUE (wishlist_id, product_id),
                                CONSTRAINT fk_items_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_items_wishlist_added ON wishlist_items (wishlist_id, added_at, id);
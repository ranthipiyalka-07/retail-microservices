CREATE TABLE categories (
                            id         BIGINT       NOT NULL AUTO_INCREMENT,
                            name       VARCHAR(100) NOT NULL,
                            parent_id  BIGINT       NULL,
                            slug       VARCHAR(120) NOT NULL,
                            active     BOOLEAN      NOT NULL DEFAULT TRUE,
                            created_at DATETIME(6)  NOT NULL,
                            updated_at DATETIME(6)  NOT NULL,
                            PRIMARY KEY (id),
                            CONSTRAINT uk_categories_slug UNIQUE (slug),
                            CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
                          id           BIGINT        NOT NULL AUTO_INCREMENT,
                          sku          VARCHAR(64)   NOT NULL,
                          name         VARCHAR(255)  NOT NULL,
                          description  TEXT          NULL,
                          brand        VARCHAR(100)  NULL,
                          category_id  BIGINT        NOT NULL,
                          base_price   DECIMAL(12,2) NOT NULL,
                          status       VARCHAR(20)   NOT NULL,
                          avg_rating   DECIMAL(3,2)  NOT NULL DEFAULT 0,
                          review_count INT           NOT NULL DEFAULT 0,
                          created_at   DATETIME(6)   NOT NULL,
                          updated_at   DATETIME(6)   NOT NULL,
                          version      INT           NOT NULL DEFAULT 0,
                          PRIMARY KEY (id),
                          CONSTRAINT uk_products_sku UNIQUE (sku),
                          CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_products_category     ON products (category_id);
CREATE INDEX idx_products_brand        ON products (brand);
CREATE INDEX idx_products_status_price ON products (status, base_price);
CREATE INDEX idx_products_name         ON products (name);

CREATE TABLE product_images (
                                id         BIGINT       NOT NULL AUTO_INCREMENT,
                                product_id BIGINT       NOT NULL,
                                url        VARCHAR(500) NOT NULL,
                                sort_order INT          NOT NULL DEFAULT 0,
                                is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
                                PRIMARY KEY (id),
                                CONSTRAINT fk_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_images_product ON product_images (product_id);

CREATE TABLE product_attributes (
                                    id         BIGINT       NOT NULL AUTO_INCREMENT,
                                    product_id BIGINT       NOT NULL,
                                    attr_name  VARCHAR(100) NOT NULL,
                                    attr_value VARCHAR(255) NOT NULL,
                                    PRIMARY KEY (id),
                                    CONSTRAINT fk_attributes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_attributes_product ON product_attributes (product_id);
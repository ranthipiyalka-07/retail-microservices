CREATE TABLE customers (
                           id                BIGINT       NOT NULL AUTO_INCREMENT,
                           first_name        VARCHAR(100) NOT NULL,
                           last_name         VARCHAR(100) NOT NULL,
                           email             VARCHAR(255) NOT NULL,
                           phone             VARBINARY(255) NULL,          -- AES-256 encrypted
                           status            VARCHAR(20)  NOT NULL,
                           consent_marketing BOOLEAN      NOT NULL DEFAULT FALSE,
                           consent_at        DATETIME(6)  NULL,
                           created_at        DATETIME(6)  NOT NULL,
                           updated_at        DATETIME(6)  NOT NULL,
                           version           INT          NOT NULL DEFAULT 0,
                           PRIMARY KEY (id),
                           CONSTRAINT uk_customers_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_addresses (
                                    id          BIGINT       NOT NULL AUTO_INCREMENT,
                                    customer_id BIGINT       NOT NULL,
                                    type        VARCHAR(20)  NOT NULL,
                                    line1       VARBINARY(512) NOT NULL,            -- AES-256 encrypted
                                    line2       VARBINARY(512) NULL,                -- AES-256 encrypted
                                    city        VARCHAR(100) NOT NULL,
                                    state       VARCHAR(100) NULL,
                                    postal_code VARCHAR(20)  NOT NULL,
                                    country     VARCHAR(2)   NOT NULL,
                                    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at  DATETIME(6)  NOT NULL,
                                    updated_at  DATETIME(6)  NOT NULL,
                                    PRIMARY KEY (id),
                                    CONSTRAINT fk_address_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_address_customer ON customer_addresses (customer_id);
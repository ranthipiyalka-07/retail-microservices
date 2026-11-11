CREATE TABLE campaigns (
                           id             BIGINT        NOT NULL AUTO_INCREMENT,
                           name           VARCHAR(150)  NOT NULL,
                           description    VARCHAR(500)  NULL,
                           discount_type  VARCHAR(20)   NOT NULL,
                           discount_value DECIMAL(10,2) NOT NULL,
                           start_at       DATETIME(6)   NOT NULL,
                           end_at         DATETIME(6)   NOT NULL,
                           status         VARCHAR(20)   NOT NULL,
                           created_by     VARCHAR(255)  NOT NULL,
                           created_at     DATETIME(6)   NOT NULL,
                           updated_at     DATETIME(6)   NOT NULL,
                           PRIMARY KEY (id),
                           CONSTRAINT ck_campaigns_value      CHECK (discount_value > 0),
                           CONSTRAINT ck_campaigns_percentage CHECK (discount_type <> 'PERCENTAGE' OR discount_value < 100),
                           CONSTRAINT ck_campaigns_window     CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_campaigns_status_window ON campaigns (status, start_at, end_at);

CREATE TABLE campaign_targets (
                                  id          BIGINT      NOT NULL AUTO_INCREMENT,
                                  campaign_id BIGINT      NOT NULL,
                                  target_type VARCHAR(20) NOT NULL,          -- PRODUCT or CATEGORY
                                  target_id   BIGINT      NOT NULL,          -- a plain id: the product or category lives in product-service
                                  PRIMARY KEY (id),
                                  CONSTRAINT uk_campaign_target UNIQUE (campaign_id, target_type, target_id),
                                  CONSTRAINT fk_targets_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_targets_lookup ON campaign_targets (target_type, target_id);
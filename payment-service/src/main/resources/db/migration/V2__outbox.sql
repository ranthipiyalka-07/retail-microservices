CREATE TABLE outbox_events (
                               id              BIGINT       NOT NULL AUTO_INCREMENT,
                               event_id        VARCHAR(36)  NOT NULL,
                               topic           VARCHAR(100) NOT NULL,
                               event_key       VARCHAR(100) NULL,
                               event_type      VARCHAR(100) NOT NULL,
                               payload         MEDIUMTEXT   NOT NULL,
                               attempts        INT          NOT NULL DEFAULT 0,
                               next_attempt_at DATETIME(6)  NOT NULL,
                               locked_until    DATETIME(6)  NULL,
                               last_error      VARCHAR(500) NULL,
                               created_at      DATETIME(6)  NOT NULL,
                               sent_at         DATETIME(6)  NULL,
                               PRIMARY KEY (id),
                               CONSTRAINT uk_outbox_event UNIQUE (event_id, topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_outbox_unsent ON outbox_events (sent_at, id);
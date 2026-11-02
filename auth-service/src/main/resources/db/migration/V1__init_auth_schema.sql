CREATE TABLE roles (
                       id   BIGINT      NOT NULL AUTO_INCREMENT,
                       name VARCHAR(50) NOT NULL,
                       PRIMARY KEY (id),
                       CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users_credentials (
                                   id              BIGINT       NOT NULL AUTO_INCREMENT,
                                   customer_id     BIGINT       NULL,
                                   email           VARCHAR(255) NOT NULL,
                                   password_hash   VARCHAR(255) NOT NULL,
                                   status          VARCHAR(30)  NOT NULL,
                                   failed_attempts INT          NOT NULL DEFAULT 0,
                                   locked_until    DATETIME(6)  NULL,
                                   last_login_at   DATETIME(6)  NULL,
                                   created_at      DATETIME(6)  NOT NULL,
                                   updated_at      DATETIME(6)  NOT NULL,
                                   version         INT          NOT NULL DEFAULT 0,
                                   PRIMARY KEY (id),
                                   CONSTRAINT uk_users_email    UNIQUE (email),
                                   CONSTRAINT uk_users_customer UNIQUE (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users_credentials (id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
                                id          BIGINT       NOT NULL AUTO_INCREMENT,
                                user_id     BIGINT       NOT NULL,
                                token_hash  VARCHAR(64)  NOT NULL,
                                expires_at  DATETIME(6)  NOT NULL,
                                revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
                                device_info VARCHAR(255) NULL,
                                created_at  DATETIME(6)  NOT NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
                                CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users_credentials (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_refresh_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

INSERT INTO roles (name) VALUES
                             ('CUSTOMER'), ('STORE_MANAGER'), ('MARKETING_MANAGER'), ('SUPPORT_AGENT'), ('ADMIN');
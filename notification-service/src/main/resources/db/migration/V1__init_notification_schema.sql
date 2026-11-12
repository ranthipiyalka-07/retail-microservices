CREATE TABLE notification_templates (
                                        id           BIGINT       NOT NULL AUTO_INCREMENT,
                                        code         VARCHAR(50)  NOT NULL,
                                        channel      VARCHAR(10)  NOT NULL,
                                        subject      VARCHAR(200) NOT NULL,
                                        body         TEXT         NOT NULL,
                                        placeholders VARCHAR(255) NOT NULL,          -- the {{names}} a template may use, comma separated
                                        active       BOOLEAN      NOT NULL DEFAULT TRUE,
                                        updated_at   DATETIME(6)  NOT NULL,
                                        updated_by   VARCHAR(255) NOT NULL,
                                        PRIMARY KEY (id),
                                        CONSTRAINT uk_templates_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_log (
                                  id              BIGINT       NOT NULL AUTO_INCREMENT,
                                  event_id        VARCHAR(36)  NOT NULL,
                                  event_type      VARCHAR(40)  NOT NULL,
                                  recipient_email VARCHAR(255) NOT NULL,
                                  template_code   VARCHAR(50)  NOT NULL,
                                  subject         VARCHAR(255) NOT NULL,        -- the rendered email: a retry sends exactly this
                                  body            TEXT         NOT NULL,
                                  status          VARCHAR(10)  NOT NULL,        -- PENDING, SENT, FAILED
                                  attempts        INT          NOT NULL DEFAULT 0,
                                  next_attempt_at DATETIME(6)  NOT NULL,
                                  locked_until    DATETIME(6)  NULL,            -- a lease: whoever claimed the email is sending it until then
                                  last_error      VARCHAR(500) NULL,
                                  reference_type  VARCHAR(30)  NULL,
                                  reference_id    VARCHAR(64)  NULL,
                                  created_at      DATETIME(6)  NOT NULL,
                                  sent_at         DATETIME(6)  NULL,
                                  updated_at      DATETIME(6)  NOT NULL,
                                  PRIMARY KEY (id),
    -- The same event is never emailed twice to the same address, however often it arrives.
                                  CONSTRAINT uk_log_event_recipient UNIQUE (event_id, recipient_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_log_due     ON notification_log (status, next_attempt_at);
CREATE INDEX idx_log_created ON notification_log (created_at);

INSERT INTO notification_templates (code, channel, subject, body, placeholders, active, updated_at, updated_by) VALUES
                                                                                                                    ('CUSTOMER_REGISTERED', 'EMAIL',
                                                                                                                     'Welcome to {{shopName}}',
                                                                                                                     'Hello {{firstName}},\n\nWelcome to {{shopName}}! Your account has been created. You can sign in with this email address ({{email}}).\n\nHappy shopping!\n\n{{shopName}}',
                                                                                                                     'firstName,email,shopName', TRUE, UTC_TIMESTAMP(6), 'system'),
                                                                                                                    ('ORDER_CONFIRMED', 'EMAIL',
                                                                                                                     'Your order {{orderNumber}} is confirmed',
                                                                                                                     'Thank you for your order!\n\nOrder number: {{orderNumber}}\nItems: {{itemCount}}\nTotal: {{total}}\n\nWe will email you again when it ships.\n\n{{shopName}}',
                                                                                                                     'orderNumber,itemCount,total,shopName', TRUE, UTC_TIMESTAMP(6), 'system'),
                                                                                                                    ('ORDER_STATUS_CHANGED', 'EMAIL',
                                                                                                                     'Update on your order {{orderNumber}}',
                                                                                                                     'Hello,\n\nYour order {{orderNumber}} {{statusLabel}}.\n\n{{trackingDetails}}\n\nThank you for shopping with {{shopName}}.',
                                                                                                                     'orderNumber,statusLabel,trackingDetails,shopName', TRUE, UTC_TIMESTAMP(6), 'system'),
                                                                                                                    ('SUPPORT_TICKET_CREATED', 'EMAIL',
                                                                                                                     'We received your request {{ticketNumber}}',
                                                                                                                     'Hello,\n\nWe received your support request and will get back to you soon.\n\nReference: {{ticketNumber}}\nSubject: {{subject}}\n\n{{shopName}} Support',
                                                                                                                     'ticketNumber,subject,shopName', TRUE, UTC_TIMESTAMP(6), 'system'),
                                                                                                                    ('SUPPORT_TICKET_REPLIED', 'EMAIL',
                                                                                                                     'New reply on your request {{ticketNumber}}',
                                                                                                                     'Hello,\n\nOur support team replied to your request {{ticketNumber}} ({{subject}}). Please sign in to your account to read the reply.\n\n{{shopName}} Support',
                                                                                                                     'ticketNumber,subject,shopName', TRUE, UTC_TIMESTAMP(6), 'system'),
                                                                                                                    ('LOW_STOCK_ALERT', 'EMAIL',
                                                                                                                     'Low stock: product {{productId}}',
                                                                                                                     'Product {{productId}} is running low.\n\nAvailable: {{available}}\nReorder level: {{reorderLevel}}\n\n{{shopName}} inventory',
                                                                                                                     'productId,available,reorderLevel,shopName', TRUE, UTC_TIMESTAMP(6), 'system');
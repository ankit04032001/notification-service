-- notification_log: main notification record

CREATE TABLE notification_log (
    id                  UUID            PRIMARY KEY,
    idempotency_key     VARCHAR(255)    NOT NULL UNIQUE,
    channel             VARCHAR(10)     NOT NULL,
    priority            VARCHAR(10)     NOT NULL,
    recipient           VARCHAR(320)    NOT NULL,
    subject             TEXT,
    body                TEXT            NOT NULL,
    template_id         VARCHAR(100),
    template_params     JSONB,
    status              VARCHAR(20)     NOT NULL,
    provider_used       VARCHAR(50),
    provider_message_id VARCHAR(255),
    retry_count         INT             NOT NULL DEFAULT 0,
    max_retries         INT             NOT NULL DEFAULT 3,
    error_message       TEXT,
    metadata            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMPTZ
);

-- Enum value constraints
ALTER TABLE notification_log
    ADD CONSTRAINT chk_notification_log_channel
        CHECK (channel IN ('EMAIL', 'SMS', 'PUSH'));

ALTER TABLE notification_log
    ADD CONSTRAINT chk_notification_log_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));

ALTER TABLE notification_log
    ADD CONSTRAINT chk_notification_log_status
        CHECK (status IN ('ACCEPTED', 'QUEUED', 'PROCESSING', 'DELIVERED', 'FAILED', 'DLQ'));

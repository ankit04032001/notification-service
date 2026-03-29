-- notification_status_history: audit trail for status transitions

CREATE TABLE notification_status_history (
    id                  BIGSERIAL       PRIMARY KEY,
    notification_id     UUID            NOT NULL REFERENCES notification_log(id),
    from_status         VARCHAR(20),
    to_status           VARCHAR(20)     NOT NULL,
    provider_name       VARCHAR(50),
    error_message       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status_history_from_status
        CHECK (from_status IS NULL OR from_status IN ('ACCEPTED', 'QUEUED', 'PROCESSING', 'DELIVERED', 'FAILED', 'DLQ')),
    CONSTRAINT chk_status_history_to_status
        CHECK (to_status IN ('ACCEPTED', 'QUEUED', 'PROCESSING', 'DELIVERED', 'FAILED', 'DLQ'))
);

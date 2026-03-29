-- provider_config: routing config for notification providers

CREATE TABLE provider_config (
    id              BIGSERIAL       PRIMARY KEY,
    channel         VARCHAR(10)     NOT NULL,
    provider_name   VARCHAR(50)     NOT NULL,
    priority        INT             NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    weight          INT             NOT NULL DEFAULT 100,
    config          JSONB,
    rate_limit_rps  INT,

    CONSTRAINT uq_provider_config_channel_name UNIQUE (channel, provider_name),
    CONSTRAINT chk_provider_config_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH'))
);

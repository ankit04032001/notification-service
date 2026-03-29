-- Performance indexes for common query patterns

-- notification_log indexes
CREATE INDEX idx_notification_log_status     ON notification_log (status);
CREATE INDEX idx_notification_log_channel    ON notification_log (channel);
CREATE INDEX idx_notification_log_recipient  ON notification_log (recipient);
CREATE INDEX idx_notification_log_created_at ON notification_log (created_at);

-- Composite for consumer queries
CREATE INDEX idx_notification_log_channel_status ON notification_log (channel, status);

-- notification_status_history indexes
CREATE INDEX idx_status_history_notification_id ON notification_status_history (notification_id);
CREATE INDEX idx_status_history_created_at      ON notification_status_history (created_at);

-- provider_config indexes
CREATE INDEX idx_provider_config_channel_active ON provider_config (channel, is_active);

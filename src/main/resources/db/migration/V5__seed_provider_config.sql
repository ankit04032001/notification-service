-- Seed default providers: priority 1 = primary, 2 = fallback
INSERT INTO provider_config (channel, provider_name, priority, is_active, weight) VALUES
    ('EMAIL', 'SENDGRID', 1, true, 100),
    ('EMAIL', 'AWS_SES',  2, true, 100),
    ('SMS',   'TWILIO',   1, true, 100),
    ('SMS',   'AWS_SNS',  2, true, 100),
    ('PUSH',  'FCM',      1, true, 100),
    ('PUSH',  'APNS',     2, true, 100);

package com.notificationservice.notification_service.provider;

import com.notificationservice.notification_service.dto.NotificationEvent;

/** Strategy interface for notification delivery providers. */
public interface NotificationProvider {

    ProviderResult send(NotificationEvent event);

    /**
     * @return the provider name as stored in provider_config (e.g., "SENDGRID", "TWILIO")
     */
    String getProviderName();
}

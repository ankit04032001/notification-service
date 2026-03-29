package com.notificationservice.notification_service.dto;

import com.notificationservice.notification_service.enums.ChannelType;
import com.notificationservice.notification_service.enums.Priority;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Kafka message payload for notification processing
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {

    private UUID notificationId;
    private String idempotencyKey;
    private ChannelType channel;
    private Priority priority;
    private String recipient;
    private String subject;
    private String body;
    private String templateId;
    private Map<String, Object> templateParams;
    private Map<String, Object> metadata;
    private int retryCount;
    private int maxRetries;
    private Instant createdAt;
}

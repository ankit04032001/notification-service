package com.notificationservice.notification_service.dto;

import com.notificationservice.notification_service.enums.ChannelType;
import com.notificationservice.notification_service.enums.NotificationStatus;
import com.notificationservice.notification_service.enums.Priority;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private UUID id;
    private String idempotencyKey;
    private ChannelType channel;
    private Priority priority;
    private String recipient;
    private NotificationStatus status;
    private String providerUsed;
    private int retryCount;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deliveredAt;
}

package com.notificationservice.notification_service.dto;

import com.notificationservice.notification_service.enums.NotificationStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistoryResponse {

    private NotificationStatus fromStatus;
    private NotificationStatus toStatus;
    private String providerName;
    private String errorMessage;
    private Instant timestamp;
}

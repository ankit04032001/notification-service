package com.notificationservice.notification_service.dto;

import com.notificationservice.notification_service.enums.ChannelType;
import com.notificationservice.notification_service.enums.Priority;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    @NotNull(message = "Channel is required")
    private ChannelType channel;

    @NotBlank(message = "Recipient is required")
    @Size(max = 320, message = "Recipient must not exceed 320 characters")
    private String recipient;

    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    @NotBlank(message = "Body is required")
    @Size(max = 10000, message = "Body must not exceed 10,000 characters")
    private String body;

    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Size(max = 100, message = "Template ID must not exceed 100 characters")
    private String templateId;

    private Map<String, Object> templateParams;

    private Map<String, Object> metadata;
}

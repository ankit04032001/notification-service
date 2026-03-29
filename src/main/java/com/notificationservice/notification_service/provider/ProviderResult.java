package com.notificationservice.notification_service.provider;

import lombok.*;

/** Result of a provider delivery attempt. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderResult {

    private boolean success;
    private String providerMessageId;
    private String errorMessage;

    public static ProviderResult success(String providerMessageId) {
        return ProviderResult.builder()
                .success(true)
                .providerMessageId(providerMessageId)
                .build();
    }

    public static ProviderResult failure(String errorMessage) {
        return ProviderResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}

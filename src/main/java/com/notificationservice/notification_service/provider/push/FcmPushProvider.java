package com.notificationservice.notification_service.provider.push;

import com.notificationservice.notification_service.dto.NotificationEvent;
import com.notificationservice.notification_service.provider.NotificationProvider;
import com.notificationservice.notification_service.provider.ProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Mock provider - simulates FCM with ~95% success rate
@Component
@Slf4j
public class FcmPushProvider implements NotificationProvider {

    private static final String PROVIDER_NAME = "FCM";
    private static final double SUCCESS_RATE = 0.95;

    @Override
    @CircuitBreaker(name = "FCM", fallbackMethod = "sendFallback")
    @Retry(name = "FCM")
    public ProviderResult send(NotificationEvent event) {
        log.debug("FCM sending push to {}", event.getRecipient());
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE) {
            String messageId = "fcm-" + UUID.randomUUID();
            log.info("FCM delivered: recipient={}, messageId={}", event.getRecipient(), messageId);
            return ProviderResult.success(messageId);
        }

        throw new RuntimeException("FCM: simulated transient failure");
    }

    private ProviderResult sendFallback(NotificationEvent event, Exception e) {
        log.warn("FCM circuit-breaker fallback: {}", e.getMessage());
        return ProviderResult.failure("FCM: " + e.getMessage());
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 201));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

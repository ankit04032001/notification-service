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

// Mock provider - simulates APNs with ~90% success rate (fallback for FCM)
@Component
@Slf4j
public class ApnsPushProvider implements NotificationProvider {

    private static final String PROVIDER_NAME = "APNS";
    private static final double SUCCESS_RATE = 0.90;

    @Override
    @CircuitBreaker(name = "APNS", fallbackMethod = "sendFallback")
    @Retry(name = "APNS")
    public ProviderResult send(NotificationEvent event) {
        log.debug("APNs sending push to {}", event.getRecipient());
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE) {
            String messageId = "apns-" + UUID.randomUUID();
            log.info("APNs delivered: recipient={}, messageId={}", event.getRecipient(), messageId);
            return ProviderResult.success(messageId);
        }

        throw new RuntimeException("APNS: simulated transient failure");
    }

    private ProviderResult sendFallback(NotificationEvent event, Exception e) {
        log.warn("APNs circuit-breaker fallback: {}", e.getMessage());
        return ProviderResult.failure("APNS: " + e.getMessage());
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

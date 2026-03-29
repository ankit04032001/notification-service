package com.notificationservice.notification_service.provider.sms;

import com.notificationservice.notification_service.dto.NotificationEvent;
import com.notificationservice.notification_service.provider.NotificationProvider;
import com.notificationservice.notification_service.provider.ProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Mock provider - simulates AWS SNS with ~90% success rate (fallback for Twilio)
@Component
@Slf4j
public class AwsSnsSmsProvider implements NotificationProvider {

    private static final String PROVIDER_NAME = "AWS_SNS";
    private static final double SUCCESS_RATE = 0.90;

    @Override
    @CircuitBreaker(name = "AWS_SNS", fallbackMethod = "sendFallback")
    @Retry(name = "AWS_SNS")
    public ProviderResult send(NotificationEvent event) {
        log.debug("AWS SNS sending SMS to {}", event.getRecipient());
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE) {
            String messageId = "sns-" + UUID.randomUUID();
            log.info("AWS SNS delivered: recipient={}, messageId={}", event.getRecipient(), messageId);
            return ProviderResult.success(messageId);
        }

        throw new RuntimeException("AWS_SNS: simulated transient failure");
    }

    private ProviderResult sendFallback(NotificationEvent event, Exception e) {
        log.warn("AWS SNS circuit-breaker fallback: {}", e.getMessage());
        return ProviderResult.failure("AWS_SNS: " + e.getMessage());
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

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

// Mock provider - simulates Twilio with ~95% success rate
@Component
@Slf4j
public class TwilioSmsProvider implements NotificationProvider {

    private static final String PROVIDER_NAME = "TWILIO";
    private static final double SUCCESS_RATE = 0.95;

    @Override
    @CircuitBreaker(name = "TWILIO", fallbackMethod = "sendFallback")
    @Retry(name = "TWILIO")
    public ProviderResult send(NotificationEvent event) {
        log.debug("Twilio sending SMS to {}", event.getRecipient());
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE) {
            String messageId = "twl-" + UUID.randomUUID();
            log.info("Twilio delivered: recipient={}, messageId={}", event.getRecipient(), messageId);
            return ProviderResult.success(messageId);
        }

        throw new RuntimeException("TWILIO: simulated transient failure");
    }

    private ProviderResult sendFallback(NotificationEvent event, Exception e) {
        log.warn("Twilio circuit-breaker fallback: {}", e.getMessage());
        return ProviderResult.failure("TWILIO: " + e.getMessage());
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

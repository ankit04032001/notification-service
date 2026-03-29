package com.notificationservice.notification_service.provider.email;

import com.notificationservice.notification_service.dto.NotificationEvent;
import com.notificationservice.notification_service.provider.NotificationProvider;
import com.notificationservice.notification_service.provider.ProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Mock provider - simulates SendGrid with ~95% success rate
@Component
@Slf4j
public class SendGridEmailProvider implements NotificationProvider {

    private static final String PROVIDER_NAME = "SENDGRID";
    private static final double SUCCESS_RATE = 0.95;

    @Override
    @CircuitBreaker(name = "SENDGRID", fallbackMethod = "sendFallback")
    @Retry(name = "SENDGRID")
    public ProviderResult send(NotificationEvent event) {
        log.debug("SendGrid sending email to {}: subject='{}'", event.getRecipient(), event.getSubject());
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE) {
            String messageId = "sg-" + UUID.randomUUID();
            log.info("SendGrid delivered: recipient={}, messageId={}", event.getRecipient(), messageId);
            return ProviderResult.success(messageId);
        }

        throw new RuntimeException("SendGrid: simulated transient failure");
    }

    private ProviderResult sendFallback(NotificationEvent event, Exception e) {
        log.warn("SendGrid circuit-breaker fallback: {}", e.getMessage());
        return ProviderResult.failure("SENDGRID: " + e.getMessage());
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

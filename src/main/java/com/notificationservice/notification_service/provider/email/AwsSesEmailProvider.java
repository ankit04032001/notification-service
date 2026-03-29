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

// Mock provider - simulates AWS SES with ~90% success rate (fallback for SendGrid)
@Component
@Slf4j
public class AwsSesEmailProvider implements NotificationProvider {

    private static final String PROVIDER_NAME = "AWS_SES";
    private static final double SUCCESS_RATE = 0.90;

    @Override
    @CircuitBreaker(name = "AWS_SES", fallbackMethod = "sendFallback")
    @Retry(name = "AWS_SES")
    public ProviderResult send(NotificationEvent event) {
        log.debug("AWS SES sending email to {}: subject='{}'", event.getRecipient(), event.getSubject());
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE) {
            String messageId = "ses-" + UUID.randomUUID();
            log.info("AWS SES delivered: recipient={}, messageId={}", event.getRecipient(), messageId);
            return ProviderResult.success(messageId);
        }

        throw new RuntimeException("AWS_SES: simulated transient failure");
    }

    private ProviderResult sendFallback(NotificationEvent event, Exception e) {
        log.warn("AWS SES circuit-breaker fallback: {}", e.getMessage());
        return ProviderResult.failure("AWS_SES: " + e.getMessage());
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

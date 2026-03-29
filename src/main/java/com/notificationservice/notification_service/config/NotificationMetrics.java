package com.notificationservice.notification_service.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Centralized custom metrics for the notification service.
 *
 * Registers and exposes counters and timers via Micrometer's MeterRegistry,
 * which are automatically scraped by Prometheus via /actuator/prometheus.
 *
 * Metrics:
 *  - notification.api.requests   (Counter)  — API submissions, tagged by channel + priority
 *  - notification.api.latency    (Timer)    — API response time (p50/p95/p99)
 *  - notification.consumer.processed (Counter) — Consumer completions, tagged by channel + status
 *  - notification.consumer.latency   (Timer)   — Consumer processing time
 *  - notification.provider.success   (Counter) — Provider delivery successes, tagged by provider + channel
 *  - notification.provider.failure   (Counter) — Provider delivery failures, tagged by provider + channel
 *  - notification.dlq.count          (Counter) — Notifications moved to DLQ
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    //API Layer Metrics 

    public void incrementApiRequests(String channel, String priority) {
        Counter.builder("notification.api.requests")
                .description("Total API notification submissions")
                .tag("channel", channel)
                .tag("priority", priority)
                .register(registry)
                .increment();
    }

    public Timer.Sample startApiTimer() {
        return Timer.start(registry);
    }

    public void stopApiTimer(Timer.Sample sample, String channel, String status) {
        sample.stop(Timer.builder("notification.api.latency")
                .description("API response latency")
                .tag("channel", channel)
                .tag("status", status)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
    }

    //Consumer Layer Metrics

    public void incrementConsumerProcessed(String channel, String outcome) {
        Counter.builder("notification.consumer.processed")
                .description("Notifications processed by consumer")
                .tag("channel", channel)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public Timer.Sample startConsumerTimer() {
        return Timer.start(registry);
    }

    public void stopConsumerTimer(Timer.Sample sample, String channel) {
        sample.stop(Timer.builder("notification.consumer.latency")
                .description("Consumer processing latency")
                .tag("channel", channel)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
    }

    //Provider Layer Metrics 

    public void incrementProviderSuccess(String provider, String channel) {
        Counter.builder("notification.provider.success")
                .description("Successful provider deliveries")
                .tag("provider", provider)
                .tag("channel", channel)
                .register(registry)
                .increment();
    }

    public void incrementProviderFailure(String provider, String channel) {
        Counter.builder("notification.provider.failure")
                .description("Failed provider deliveries")
                .tag("provider", provider)
                .tag("channel", channel)
                .register(registry)
                .increment();
    }

    //DLQ Metrics

    public void incrementDlq(String channel) {
        Counter.builder("notification.dlq.count")
                .description("Notifications moved to dead-letter queue")
                .tag("channel", channel)
                .register(registry)
                .increment();
    }
}

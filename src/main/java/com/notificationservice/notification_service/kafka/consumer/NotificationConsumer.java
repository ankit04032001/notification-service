package com.notificationservice.notification_service.kafka.consumer;

import com.notificationservice.notification_service.config.NotificationMetrics;
import com.notificationservice.notification_service.dto.NotificationEvent;
import com.notificationservice.notification_service.entity.NotificationLog;
import com.notificationservice.notification_service.enums.NotificationStatus;
import com.notificationservice.notification_service.idempotency.DistributedLockService;
import com.notificationservice.notification_service.kafka.producer.NotificationProducer;
import com.notificationservice.notification_service.provider.ProviderResult;
import com.notificationservice.notification_service.provider.ProviderStrategyFactory;
import com.notificationservice.notification_service.repository.NotificationLogRepository;
import com.notificationservice.notification_service.service.NotificationService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Consumes notifications from Kafka for async processing.
 * Uses distributed locking to prevent concurrent processing of the same notification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private static final Set<NotificationStatus> SKIP_STATUSES = Set.of(
            NotificationStatus.DELIVERED,
            NotificationStatus.PROCESSING,
            NotificationStatus.DLQ
    );

    private final NotificationLogRepository notificationLogRepository;
    private final DistributedLockService distributedLockService;
    private final NotificationProducer notificationProducer;
    private final NotificationService notificationService;
    private final ProviderStrategyFactory providerStrategyFactory;
    private final NotificationMetrics metrics;

    @KafkaListener(topics = "notification.requests", groupId = "notification-service-group")
    public void consumeMainTopic(NotificationEvent event, Acknowledgment acknowledgment) {
        processNotification(event, acknowledgment);
    }

    @KafkaListener(topics = "notification.retry", groupId = "notification-service-group")
    public void consumeRetryTopic(NotificationEvent event, Acknowledgment acknowledgment) {
        processNotification(event, acknowledgment);
    }

    private void processNotification(NotificationEvent event, Acknowledgment acknowledgment) {
        UUID notificationId = event.getNotificationId();
        String channel = event.getChannel().name();
        String lockToken = null;
        Timer.Sample timerSample = metrics.startConsumerTimer();

        // Set MDC for log correlation
        MDC.put("notificationId", notificationId.toString());

        try {
            // Acquire distributed lock to prevent duplicate processing
            lockToken = distributedLockService.acquireLock(notificationId);
            if (lockToken == null) {
                log.info("Could not acquire lock, already being processed: notificationId={}", notificationId);
                acknowledgment.acknowledge();
                return;
            }

            // Load from DB and verify status
            NotificationLog notification = notificationLogRepository.findById(notificationId).orElse(null);
            if (notification == null) {
                log.warn("Notification not found in DB, skipping: notificationId={}", notificationId);
                acknowledgment.acknowledge();
                return;
            }

            if (SKIP_STATUSES.contains(notification.getStatus())) {
                log.info("Notification already {}, skipping: notificationId={}", notification.getStatus(), notificationId);
                acknowledgment.acknowledge();
                return;
            }

            // Transition to PROCESSING
            NotificationStatus previousStatus = notification.getStatus();
            notification.setStatus(NotificationStatus.PROCESSING);
            notificationLogRepository.save(notification);
            notificationService.recordStatusTransition(
                    notificationId, previousStatus, NotificationStatus.PROCESSING, null, null);

            // Dispatch to provider chain
            deliverNotification(event, notification);

        } catch (Exception e) {
            log.error("Unexpected error processing notification: notificationId={}", notificationId, e);
        } finally {
            metrics.stopConsumerTimer(timerSample, channel);
            if (lockToken != null) {
                distributedLockService.releaseLock(notificationId, lockToken);
            }
            MDC.remove("notificationId");
            acknowledgment.acknowledge();
        }
    }

    /** Attempts delivery via provider chain (primary -> fallback). */
    private void deliverNotification(NotificationEvent event, NotificationLog notification) {
        UUID notificationId = notification.getId();
        try {
            ProviderResult result = providerStrategyFactory.dispatch(event);

            if (result.isSuccess()) {
                notification.setStatus(NotificationStatus.DELIVERED);
                notification.setProviderUsed(resolveProviderName(result, event));
                notification.setProviderMessageId(result.getProviderMessageId());
                notification.setDeliveredAt(Instant.now());
                notificationLogRepository.save(notification);

                notificationService.recordStatusTransition(
                        notificationId, NotificationStatus.PROCESSING, NotificationStatus.DELIVERED,
                        notification.getProviderUsed(), null);

                metrics.incrementConsumerProcessed(event.getChannel().name(), "delivered");

                log.info("Notification delivered: id={}, channel={}, provider={}, providerMsgId={}",
                        notificationId, event.getChannel(), notification.getProviderUsed(), result.getProviderMessageId());
            } else {
                // All providers in the chain failed
                handleProviderFailure(event, notification,
                        new RuntimeException(result.getErrorMessage()));
            }

        } catch (Exception providerException) {
            handleProviderFailure(event, notification, providerException);
        }
    }

    /** Resolves provider name from message ID prefix, falls back to primary config. */
    private String resolveProviderName(ProviderResult result, NotificationEvent event) {
        String msgId = result.getProviderMessageId();
        if (msgId != null) {
            if (msgId.startsWith("sg-")) return "SENDGRID";
            if (msgId.startsWith("ses-")) return "AWS_SES";
            if (msgId.startsWith("twl-")) return "TWILIO";
            if (msgId.startsWith("sns-")) return "AWS_SNS";
            if (msgId.startsWith("fcm-")) return "FCM";
            if (msgId.startsWith("apns-")) return "APNS";
        }
        return providerStrategyFactory.resolveProviderName(event);
    }

    /** Handles failures: retries if under max, otherwise sends to DLQ. */
    private void handleProviderFailure(NotificationEvent event, NotificationLog notification, Exception ex) {
        UUID notificationId = notification.getId();
        String errorMsg = ex.getMessage();

        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setErrorMessage(errorMsg);

        if (notification.getRetryCount() < notification.getMaxRetries()) {
            // Transient failure - send to retry topic
            notification.setStatus(NotificationStatus.FAILED);
            notificationLogRepository.save(notification);

            notificationService.recordStatusTransition(
                    notificationId, NotificationStatus.PROCESSING, NotificationStatus.FAILED,
                    null, errorMsg);

            // Update event retry count before republishing
            event.setRetryCount(notification.getRetryCount());
            notificationProducer.sendToRetryTopic(event);

            metrics.incrementConsumerProcessed(event.getChannel().name(), "retry");

            log.warn("Notification failed, queued for retry ({}/{}): id={}, error={}",
                    notification.getRetryCount(), notification.getMaxRetries(), notificationId, errorMsg);

        } else {
            // Max retries exhausted - send to DLQ
            notification.setStatus(NotificationStatus.DLQ);
            notificationLogRepository.save(notification);

            notificationService.recordStatusTransition(
                    notificationId, NotificationStatus.PROCESSING, NotificationStatus.DLQ,
                    null, errorMsg);

            notificationProducer.sendToDlq(event);

            metrics.incrementConsumerProcessed(event.getChannel().name(), "dlq");
            metrics.incrementDlq(event.getChannel().name());

            log.error("Notification moved to DLQ after {} retries: id={}, error={}",
                    notification.getMaxRetries(), notificationId, errorMsg);
        }
    }
}

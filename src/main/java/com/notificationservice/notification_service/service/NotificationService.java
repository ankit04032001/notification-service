package com.notificationservice.notification_service.service;

import com.notificationservice.notification_service.dto.NotificationEvent;
import com.notificationservice.notification_service.dto.NotificationRequest;
import com.notificationservice.notification_service.dto.NotificationResponse;
import com.notificationservice.notification_service.dto.StatusHistoryResponse;
import com.notificationservice.notification_service.entity.NotificationLog;
import com.notificationservice.notification_service.entity.NotificationStatusHistory;
import com.notificationservice.notification_service.enums.NotificationStatus;
import com.notificationservice.notification_service.exception.DuplicateNotificationException;
import com.notificationservice.notification_service.exception.NotificationNotFoundException;
import com.notificationservice.notification_service.idempotency.IdempotencyService;
import com.notificationservice.notification_service.kafka.producer.NotificationProducer;
import com.notificationservice.notification_service.repository.NotificationLogRepository;
import com.notificationservice.notification_service.repository.NotificationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationStatusHistoryRepository statusHistoryRepository;
    private final IdempotencyService idempotencyService;
    private final NotificationProducer notificationProducer;

    /**
     * Accepts a notification, checks idempotency, persists, and queues for async processing.
     */
    @Transactional
    public NotificationResponse submitNotification(String idempotencyKey, NotificationRequest request) {
        // Check for cached duplicate response
        NotificationResponse cachedResponse = idempotencyService.getCachedResponse(idempotencyKey);
        if (cachedResponse != null) {
            log.info("Returning cached response for duplicate request: key={}", idempotencyKey);
            return cachedResponse;
        }

        // Atomically reserve idempotency slot (SETNX)
        if (!idempotencyService.setIfAbsent(idempotencyKey)) {
            // Another request just reserved this key but hasn't cached a response yet
            log.warn("Concurrent duplicate detected: key={}", idempotencyKey);
            throw new DuplicateNotificationException(idempotencyKey);
        }

        NotificationLog notification = NotificationLog.builder()
                .idempotencyKey(idempotencyKey)
                .channel(request.getChannel())
                .priority(request.getPriority())
                .recipient(request.getRecipient())
                .subject(request.getSubject())
                .body(request.getBody())
                .templateId(request.getTemplateId())
                .templateParams(request.getTemplateParams())
                .metadata(request.getMetadata())
                .status(NotificationStatus.ACCEPTED)
                .build();

        notification = notificationLogRepository.save(notification);

        recordStatusTransition(notification.getId(), null, NotificationStatus.ACCEPTED, null, null);

        // Publish to Kafka and transition to QUEUED
        NotificationEvent event = toEvent(notification);
        notificationProducer.sendToMainTopic(event);

        // Transition to QUEUED
        notification.setStatus(NotificationStatus.QUEUED);
        notificationLogRepository.save(notification);
        recordStatusTransition(notification.getId(), NotificationStatus.ACCEPTED, NotificationStatus.QUEUED, null, null);

        log.info("Notification accepted and queued: id={}, channel={}, recipient={}",
                notification.getId(), notification.getChannel(), notification.getRecipient());

        NotificationResponse response = toResponse(notification);

        // Cache for future duplicate checks
        idempotencyService.cacheResponse(idempotencyKey, response);

        return response;
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(UUID id) {
        NotificationLog notification = notificationLogRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getNotificationHistory(UUID id) {
        // Verify notification exists
        if (!notificationLogRepository.existsById(id)) {
            throw new NotificationNotFoundException(id);
        }

        return statusHistoryRepository.findByNotificationIdOrderByCreatedAtAsc(id)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public void recordStatusTransition(UUID notificationId, NotificationStatus fromStatus,
                                       NotificationStatus toStatus, String providerName, String errorMessage) {
        NotificationStatusHistory history = NotificationStatusHistory.builder()
                .notificationId(notificationId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .providerName(providerName)
                .errorMessage(errorMessage)
                .build();
        statusHistoryRepository.save(history);
    }

    private NotificationResponse toResponse(NotificationLog notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .idempotencyKey(notification.getIdempotencyKey())
                .channel(notification.getChannel())
                .priority(notification.getPriority())
                .recipient(notification.getRecipient())
                .status(notification.getStatus())
                .providerUsed(notification.getProviderUsed())
                .retryCount(notification.getRetryCount())
                .errorMessage(notification.getErrorMessage())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .deliveredAt(notification.getDeliveredAt())
                .build();
    }

    private NotificationEvent toEvent(NotificationLog notification) {
        return NotificationEvent.builder()
                .notificationId(notification.getId())
                .idempotencyKey(notification.getIdempotencyKey())
                .channel(notification.getChannel())
                .priority(notification.getPriority())
                .recipient(notification.getRecipient())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .templateId(notification.getTemplateId())
                .templateParams(notification.getTemplateParams())
                .metadata(notification.getMetadata())
                .retryCount(notification.getRetryCount())
                .maxRetries(notification.getMaxRetries())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private StatusHistoryResponse toHistoryResponse(NotificationStatusHistory history) {
        return StatusHistoryResponse.builder()
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .providerName(history.getProviderName())
                .errorMessage(history.getErrorMessage())
                .timestamp(history.getCreatedAt())
                .build();
    }
}

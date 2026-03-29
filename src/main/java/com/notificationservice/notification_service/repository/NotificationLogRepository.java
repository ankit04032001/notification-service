package com.notificationservice.notification_service.repository;

import com.notificationservice.notification_service.entity.NotificationLog;
import com.notificationservice.notification_service.enums.ChannelType;
import com.notificationservice.notification_service.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Optional<NotificationLog> findByIdempotencyKey(String idempotencyKey);

    List<NotificationLog> findByStatus(NotificationStatus status);

    List<NotificationLog> findByChannelAndStatus(ChannelType channel, NotificationStatus status);

    List<NotificationLog> findByRecipient(String recipient);
}

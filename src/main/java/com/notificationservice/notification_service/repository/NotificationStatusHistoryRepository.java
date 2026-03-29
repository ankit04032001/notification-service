package com.notificationservice.notification_service.repository;

import com.notificationservice.notification_service.entity.NotificationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationStatusHistoryRepository extends JpaRepository<NotificationStatusHistory, Long> {

    List<NotificationStatusHistory> findByNotificationIdOrderByCreatedAtAsc(UUID notificationId);
}

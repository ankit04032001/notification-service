package com.notificationservice.notification_service.controller;

import com.notificationservice.notification_service.config.NotificationMetrics;
import com.notificationservice.notification_service.dto.NotificationRequest;
import com.notificationservice.notification_service.dto.NotificationResponse;
import com.notificationservice.notification_service.dto.StatusHistoryResponse;
import com.notificationservice.notification_service.service.NotificationService;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMetrics metrics;

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody NotificationRequest request) {

        Timer.Sample timerSample = metrics.startApiTimer();
        String channel = request.getChannel().name();
        String priority = request.getPriority().name();

        try {
            NotificationResponse response = notificationService.submitNotification(idempotencyKey, request);
            metrics.incrementApiRequests(channel, priority);
            metrics.stopApiTimer(timerSample, channel, "accepted");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            metrics.stopApiTimer(timerSample, channel, "error");
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable UUID id) {
        NotificationResponse response = notificationService.getNotification(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusHistoryResponse>> getNotificationHistory(@PathVariable UUID id) {
        List<StatusHistoryResponse> history = notificationService.getNotificationHistory(id);
        return ResponseEntity.ok(history);
    }
}

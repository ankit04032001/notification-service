package com.notificationservice.notification_service.exception;

public class DuplicateNotificationException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateNotificationException(String idempotencyKey) {
        super("Duplicate notification request with idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

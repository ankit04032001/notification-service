package com.notificationservice.notification_service.idempotency;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.notificationservice.notification_service.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * API-layer idempotency via Redis SETNX.
 * Falls through to DB unique constraint if Redis is unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idemp:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String PENDING_VALUE = "PENDING";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public boolean setIfAbsent(String idempotencyKey) {
        try {
            String key = KEY_PREFIX + idempotencyKey;
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(key, PENDING_VALUE, DEFAULT_TTL)
                    .block();
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency check, falling through: key={}", idempotencyKey, e);
            return true; // DB unique constraint is the safety net
        }
    }

    public void cacheResponse(String idempotencyKey, NotificationResponse response) {
        try {
            String key = KEY_PREFIX + idempotencyKey;
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue()
                    .set(key, json, DEFAULT_TTL)
                    .block();
            log.debug("Cached response for idempotency key: {}", idempotencyKey);
        } catch (Exception e) {
            log.warn("Failed to cache response in Redis: key={}", idempotencyKey, e);
        }
    }

    public NotificationResponse getCachedResponse(String idempotencyKey) {
        try {
            String key = KEY_PREFIX + idempotencyKey;
            String value = redisTemplate.opsForValue()
                    .get(key)
                    .block();

            if (value == null || PENDING_VALUE.equals(value)) {
                return null;
            }

            return objectMapper.readValue(value, NotificationResponse.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize cached response: key={}", idempotencyKey, e);
            return null;
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency GET: key={}", idempotencyKey, e);
            return null;
        }
    }
}

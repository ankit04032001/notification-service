package com.notificationservice.notification_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Consumer-layer distributed lock using Redis SET NX EX.
 * Prevents concurrent processing of the same notification across consumer instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private static final String LOCK_PREFIX = "lock:process:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final ReactiveStringRedisTemplate redisTemplate;

    public String acquireLock(UUID notificationId) {
        return acquireLock(notificationId, DEFAULT_TTL);
    }

    public String acquireLock(UUID notificationId, Duration ttl) {
        String key = LOCK_PREFIX + notificationId;
        String lockToken = UUID.randomUUID().toString();
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(key, lockToken, ttl)
                    .block();
            if (Boolean.TRUE.equals(result)) {
                log.debug("Lock acquired: key={}, token={}", key, lockToken);
                return lockToken;
            }
            log.debug("Lock already held: key={}", key);
            return null;
        } catch (Exception e) {
            log.warn("Failed to acquire lock from Redis: key={}", key, e);
            return null;
        }
    }

    /**
     * Release lock only if the caller owns it (token must match).
     * Uses GET + compare + DEL - not fully atomic but safe with the status check in consumer.
     */
    public boolean releaseLock(UUID notificationId, String lockToken) {
        String key = LOCK_PREFIX + notificationId;
        try {
            String currentValue = redisTemplate.opsForValue().get(key).block();
            if (!lockToken.equals(currentValue)) {
                log.warn("Lock not owned by caller, skipping release: key={}", key);
                return false;
            }
            Long deleted = redisTemplate.delete(key).block();
            boolean released = deleted != null && deleted > 0;
            if (released) {
                log.debug("Lock released: key={}", key);
            }
            return released;
        } catch (Exception e) {
            log.warn("Failed to release lock: key={}", key, e);
            return false;
        }
    }
}

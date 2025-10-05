package com.deadlinr.app.service;

import com.deadlinr.app.config.LoginRateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter for login/register, keyed by client IP.
 * (Deliberately process-local: for a single-instance app this avoids extra
 * infra; see ADR-0003.)
 */
@Service
public class RateLimitService {

    private final LoginRateLimitProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(LoginRateLimitProperties properties) {
        this.properties = properties;
    }

    /** Consumes one token for the key; false when the bucket is empty. */
    public boolean tryConsume(String key) {
        return bucketFor(key).tryConsume(1);
    }

    /** Empties all buckets (used by tests and on config changes). */
    public void reset() {
        buckets.clear();
    }

    private Bucket bucketFor(String key) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(properties.capacity())
                    .refillGreedy(properties.capacity(), Duration.ofMinutes(properties.refillMinutes()))
                    .build();
            return Bucket.builder().addLimit(limit).build();
        });
    }
}

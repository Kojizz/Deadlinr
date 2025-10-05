package com.deadlinr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit.login")
public record LoginRateLimitProperties(int capacity, long refillMinutes) {
}

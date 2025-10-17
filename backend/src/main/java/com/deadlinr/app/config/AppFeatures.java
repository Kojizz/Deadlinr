package com.deadlinr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple feature flags, toggled via application.yml (app.features.*).
 * Each Phase-10 feature can be demoed or disabled independently.
 */
@ConfigurationProperties(prefix = "app.features")
public record AppFeatures(
        boolean apiDocs,
        boolean refreshTokens,
        boolean loginRateLimit,
        boolean observability
) {
}

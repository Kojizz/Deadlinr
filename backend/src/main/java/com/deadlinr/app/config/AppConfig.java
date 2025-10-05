package com.deadlinr.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppFeatures.class, LoginRateLimitProperties.class})
public class AppConfig {
}

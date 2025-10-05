package com.deadlinr.app.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application metrics exposed via /actuator/prometheus.
 * reminders_sent_total is registered now (reads 0) and will be incremented
 * by the reminder pipeline when it lands; the Grafana dashboard already
 * has a panel for it.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter remindersSentTotal(MeterRegistry registry) {
        return Counter.builder("reminders_sent_total")
                .description("Reminders dispatched through any channel (in-app, email)")
                .register(registry);
    }
}

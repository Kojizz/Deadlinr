package com.deadlinr.app.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class WebCacheConfig {

    /**
     * Weak ETags for the JSON API so repeat GETs can be answered with 304.
     * Weak (W/) on purpose: Tomcat skips gzip for strong-ETag responses
     * (RFC 7232 §2.3.3), so a strong ETag would silently disable
     * server.compression. GET-only and file downloads excluded:
     * ShallowEtagHeaderFilter buffers the whole response body to hash it,
     * which would load attachments into memory.
     */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter() {
            {
                setWriteWeakETag(true);
            }
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !"GET".equalsIgnoreCase(request.getMethod())
                        || request.getRequestURI().startsWith("/api/files/");
            }
        };
        FilterRegistrationBean<ShallowEtagHeaderFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/api/*");
        bean.setName("etagFilter");
        return bean;
    }
}

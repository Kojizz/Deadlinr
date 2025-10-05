package com.deadlinr.app.security;

import com.deadlinr.app.config.AppFeatures;
import com.deadlinr.app.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Applies a token bucket to POST /api/auth/login and /api/auth/register,
 * keyed by client IP. Replies 429 with the standard error body shape.
 * The whole filter is bypassed when the login-rate-limit feature flag is off.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String[] PROTECTED = {"/api/auth/login", "/api/auth/register"};

    private final RateLimitService rateLimitService;
    private final AppFeatures features;

    public LoginRateLimitFilter(RateLimitService rateLimitService, AppFeatures features) {
        this.rateLimitService = rateLimitService;
        this.features = features;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!features.loginRateLimit()) {
            return true;
        }
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod()) && (path.equals("/api/auth/oauth2/authorization/google") || path.equals("/api/auth/oauth2/authorization/github"))) return false;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        for (String protectedPath : PROTECTED) {
            if (path.equals(protectedPath)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = "login:" + request.getRemoteAddr();
        if (!rateLimitService.tryConsume(key)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Too many login attempts. Try again in a minute.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

package com.deadlinr.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.util.Optional;

/**
 * httpOnly cookies for the access and refresh tokens. The frontend never
 * touches them; the browser attaches them automatically (SameSite=Lax).
 */
@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "deadlinr_access";
    public static final String REFRESH_COOKIE = "deadlinr_refresh";

    @Value("${app.cookies.secure:false}")
    private boolean secure;

    @Value("${app.jwt.access-expiration}")
    private long accessExpirationMs;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public void setAccess(HttpServletResponse response, String token) {
        addCookie(response, ACCESS_COOKIE, token, accessExpirationMs / 1000);
    }

    public void setRefresh(HttpServletResponse response, String token) {
        addCookie(response, REFRESH_COOKIE, token, refreshExpirationMs / 1000);
    }

    public void clearAccess(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", 0);
    }

    public void clearRefresh(HttpServletResponse response) {
        addCookie(response, REFRESH_COOKIE, "", 0);
    }

    public void clearAll(HttpServletResponse response) {
        clearAccess(response);
        clearRefresh(response);
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, REFRESH_COOKIE))
                .map(cookie -> cookie.getValue())
                .filter(value -> value != null && !value.isBlank());
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

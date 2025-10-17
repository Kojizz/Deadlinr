package com.deadlinr.app.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;

public class TimedAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String SESSION_KEY = TimedAuthorizationRequestRepository.class.getName();
    public static final String COMPLETED_KEY = SESSION_KEY + ".completed";
    private final Clock clock;

    public TimedAuthorizationRequestRepository(Clock clock) { this.clock = clock; }

    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_KEY) instanceof Pending pending)) return null;
        long age = clock.millis() - pending.createdAt();
        if (age < 0 || age > 600_000) {
            session.removeAttribute(SESSION_KEY);
            return null;
        }
        String[] states = request.getParameterValues("state");
        return states != null && states.length == 1 && MessageDigest.isEqual(states[0].getBytes(StandardCharsets.UTF_8),
                pending.authorization().getState().getBytes(StandardCharsets.UTF_8)) ? pending.authorization() : null;
    }

    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request, HttpServletResponse response) {
        if (authorization == null) {
            if (request.getSession(false) != null) request.getSession(false).removeAttribute(SESSION_KEY);
            return;
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setMaxInactiveInterval(600);
        session.setAttribute(SESSION_KEY, new Pending(authorization, clock.millis()));
    }

    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorization = loadAuthorizationRequest(request);
        if (authorization != null) {
            request.getSession(false).removeAttribute(SESSION_KEY);
            request.setAttribute(COMPLETED_KEY, authorization);
        }
        return authorization;
    }

    private record Pending(OAuth2AuthorizationRequest authorization, long createdAt) implements Serializable {}
}

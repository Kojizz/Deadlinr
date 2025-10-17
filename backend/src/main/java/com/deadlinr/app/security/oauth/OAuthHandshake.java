package com.deadlinr.app.security.oauth;

import com.deadlinr.app.entity.User;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.security.AuthCookieService;
import com.deadlinr.app.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class OAuthHandshake {
    private static final String LINK_KEY = OAuthHandshake.class.getName();
    public static final String OWNER_KEY = "deadlinr_owner";
    public static final String EPOCH_KEY = "deadlinr_epoch";
    private final UserRepository users;
    private final JwtService jwt;

    public OAuthHandshake(UserRepository users, JwtService jwt) { this.users = users; this.jwt = jwt; }

    public String prepareLink(HttpServletRequest request, User user, String provider) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var session = request.getSession(true);
        session.setMaxInactiveInterval(600);
        session.setAttribute(LINK_KEY, new Link(ticket, provider, user.getId(), user.getAuthEpoch(), System.currentTimeMillis()));
        return ticket;
    }

    public OAuth2AuthorizationRequest bindLink(HttpServletRequest request, String provider, OAuth2AuthorizationRequest authorization) {
        String ticket = request.getParameter("link");
        if (ticket == null) return authorization;
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(LINK_KEY) instanceof Link link)) throw OAuthProfile.error("session_changed");
        session.removeAttribute(LINK_KEY);
        User user = localUser(request);
        if (!link.provider().equals(provider) || !user.getId().equals(link.owner()) || user.getAuthEpoch() != link.epoch()
                || System.currentTimeMillis() - link.createdAt() > 600_000
                || !MessageDigest.isEqual(ticket.getBytes(StandardCharsets.UTF_8), link.ticket().getBytes(StandardCharsets.UTF_8))) {
            throw OAuthProfile.error("session_changed");
        }
        return OAuth2AuthorizationRequest.from(authorization).attributes(attributes -> {
            attributes.put(OWNER_KEY, link.owner()); attributes.put(EPOCH_KEY, link.epoch());
        }).build();
    }

    public User localUser(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, AuthCookieService.ACCESS_COOKIE);
        if (cookie == null || jwt.isMalformed(cookie.getValue())) throw OAuthProfile.error("session_changed");
        User user = users.findByEmail(jwt.extractUsername(cookie.getValue())).orElseThrow(() -> OAuthProfile.error("session_changed"));
        if (!jwt.isTokenValid(cookie.getValue(), user)) throw OAuthProfile.error("session_changed");
        return user;
    }

    private record Link(String ticket, String provider, Long owner, long epoch, long createdAt) implements Serializable {}
}

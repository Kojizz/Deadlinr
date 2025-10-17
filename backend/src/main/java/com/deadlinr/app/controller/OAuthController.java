package com.deadlinr.app.controller;

import com.deadlinr.app.entity.User;
import com.deadlinr.app.security.oauth.*;
import com.deadlinr.app.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuthController {
    private final OAuthProviderRegistry providers;
    private final OAuthAccountService accounts;
    private final OAuthHandshake handshake;
    private final OAuthFlowHandler handler;

    public OAuthController(OAuthProviderRegistry providers, OAuthAccountService accounts, OAuthHandshake handshake, OAuthFlowHandler handler) {
        this.providers = providers; this.accounts = accounts; this.handshake = handshake; this.handler = handler;
    }

    @GetMapping("/providers")
    public Map<String, Boolean> providers(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return providers.availability();
    }

    @GetMapping({"/authorization/{provider}", "/callback/{provider}"})
    public void unavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var error = request.getAttribute(OAuthLoginConfig.ERROR_KEY);
        handler.onAuthenticationFailure(request, response, error instanceof OAuth2AuthenticationException ex ? ex : OAuthProfile.error("unavailable"));
    }

    @GetMapping("/accounts")
    public Map<String, Object> accounts(@AuthenticationPrincipal User user, HttpServletResponse response) {
        requireUser(user);
        response.setHeader("Cache-Control", "no-store");
        return Map.of("linked", accounts.linked(user.getId()), "passwordLoginEnabled", user.isPasswordLoginEnabled(), "availability", providers.availability());
    }

    @PostMapping(value = "/accounts/{provider}/link", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> link(@AuthenticationPrincipal User user, @PathVariable String provider, HttpServletRequest request) {
        requireUser(user); requireOrigin(request);
        if (!providers.enabled(provider)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This sign-in provider is not configured.");
        return Map.of("ticket", handshake.prepareLink(request, user, provider));
    }

    @DeleteMapping("/accounts/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@AuthenticationPrincipal User user, @PathVariable String provider, HttpServletRequest request) {
        requireUser(user); requireOrigin(request);
        if (!("google".equals(provider) || "github".equals(provider))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        accounts.unlink(user.getId(), provider);
    }

    private void requireUser(User user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first.");
    }
    private void requireOrigin(HttpServletRequest request) {
        if (!providers.publicUrl().equals(request.getHeader("Origin"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin does not match this workspace.");
    }
}

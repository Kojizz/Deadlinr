package com.deadlinr.app.security.oauth;

import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.AuthService;
import com.deadlinr.app.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class OAuthFlowHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final OAuthProviderRegistry providers;
    private final OAuthAccountService accounts;
    private final AuthService auth;
    private final OAuthHandshake handshake;

    public OAuthFlowHandler(OAuthProviderRegistry providers, OAuthAccountService accounts, AuthService auth, OAuthHandshake handshake) {
        this.providers = providers; this.accounts = accounts; this.auth = auth; this.handshake = handshake;
    }

    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken token)
                    || !(request.getAttribute(TimedAuthorizationRequestRepository.COMPLETED_KEY) instanceof OAuth2AuthorizationRequest authorization)) {
                throw OAuthProfile.error("expired");
            }
            String provider = token.getAuthorizedClientRegistrationId();
            OAuthProfile profile = OAuthProfile.from(provider, token.getPrincipal());
            Long owner = authorization.getAttribute(OAuthHandshake.OWNER_KEY);
            User user;
            if (owner != null) {
                Long epoch = authorization.getAttribute(OAuthHandshake.EPOCH_KEY);
                User current = handshake.localUser(request);
                if (!owner.equals(current.getId()) || epoch == null || !epoch.equals(current.getAuthEpoch())) throw OAuthProfile.error("session_changed");
                user = accounts.link(provider, profile, owner, epoch);
            } else user = accounts.login(provider, profile);
            auth.issueSocialSession(user, response);
            finish(request, response);
            response.sendRedirect(providers.publicUrl() + (owner == null ? "/dashboard" : "/settings?oauthConnected=" + provider));
        } catch (OAuth2AuthenticationException ex) {
            onAuthenticationFailure(request, response, ex);
        } catch (RuntimeException ex) {
            onAuthenticationFailure(request, response, OAuthProfile.error("failed"));
        }
    }

    @Override public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        String code = exception instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : "failed";
        if ("access_denied".equals(code)) code = "cancelled";
        if ("authorization_request_not_found".equals(code) || "invalid_state_parameter".equals(code)) code = "expired";
        if (!Set.of("cancelled", "expired", "email_conflict", "email_unverified", "identity_conflict", "session_changed", "unavailable").contains(code)) code = "failed";
        finish(request, response);
        response.sendRedirect(providers.publicUrl() + "/login?oauthError=" + code);
    }

    private void finish(HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", "").path("/").httpOnly(true)
                .secure(providers.publicUrl().startsWith("https:")).sameSite("Lax").maxAge(0).build().toString());
    }
}

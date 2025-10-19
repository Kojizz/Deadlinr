package com.deadlinr.app.security;

import com.deadlinr.app.config.AppFeatures;
import com.deadlinr.app.config.OAuthProperties;
import com.deadlinr.app.security.oauth.OAuthProviderRegistry;
import com.deadlinr.app.security.oauth.TimedAuthorizationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class OAuthRequestTest {
    @Test void absentCredentialsAndDisabledCookieSessionsNeverRegisterProviders() {
        OAuthProperties properties = new OAuthProperties(); properties.setEnabled(true);
        var features = new AppFeatures(true, true, true, true);
        assertThat(new OAuthProviderRegistry(properties, features).hasProviders()).isFalse();
        properties.setGoogleClientId("test"); properties.setGoogleClientSecret("test");
        assertThat(new OAuthProviderRegistry(properties, new AppFeatures(true, false, true, true)).hasProviders()).isFalse();
        properties.setEnabled(false);
        assertThat(new OAuthProviderRegistry(properties, features).hasProviders()).isFalse();
    }

    @Test void publicOriginCannotContainRedirectPathsOrCredentials() {
        for (String origin : new String[]{"https://safe.example/other", "https://safe.example?redirect=other", "https://user@safe.example", "http://unsafe.example"}) {
            OAuthProperties properties = new OAuthProperties(); properties.setPublicUrl(origin);
            assertThatThrownBy(() -> new OAuthProviderRegistry(properties, new AppFeatures(true, true, true, true))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void authorizationStateIsBoundToTheSessionExpiresAndIsConsumedOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);
        var repository = new TimedAuthorizationRequestRepository(clock);
        var authorization = OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://provider.example/auth")
                .clientId("test").redirectUri("http://localhost:3000/callback").state("random-state").build();
        var start = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorization, start, response);
        var callback = new MockHttpServletRequest(); callback.setSession((MockHttpSession) start.getSession()); callback.setParameter("state", "wrong-state");
        assertThat(repository.loadAuthorizationRequest(callback)).isNull();
        callback.setParameter("state", "random-state", "duplicate"); assertThat(repository.loadAuthorizationRequest(callback)).isNull();
        callback.setParameter("state", "random-state");
        assertThat(repository.removeAuthorizationRequest(callback, response)).isEqualTo(authorization);
        assertThat(repository.removeAuthorizationRequest(callback, response)).isNull();
        repository.saveAuthorizationRequest(authorization, start, response);
        assertThat(new TimedAuthorizationRequestRepository(Clock.offset(clock, Duration.ofMinutes(11))).loadAuthorizationRequest(callback)).isNull();
    }
}

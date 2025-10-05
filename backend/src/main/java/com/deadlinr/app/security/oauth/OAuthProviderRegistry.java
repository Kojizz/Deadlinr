package com.deadlinr.app.security.oauth;

import com.deadlinr.app.config.AppFeatures;
import com.deadlinr.app.config.OAuthProperties;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OAuthProviderRegistry implements ClientRegistrationRepository, Iterable<ClientRegistration> {
    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
    private final String publicUrl;

    public OAuthProviderRegistry(OAuthProperties properties, AppFeatures features) {
        URI origin = URI.create(properties.getPublicUrl());
        boolean loopback = "localhost".equals(origin.getHost()) || "127.0.0.1".equals(origin.getHost()) || "[::1]".equals(origin.getHost());
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))
                || !("https".equals(origin.getScheme()) || (loopback && "http".equals(origin.getScheme())))) {
            throw new IllegalArgumentException("APP_PUBLIC_URL must be an HTTPS origin, or HTTP localhost for development");
        }
        publicUrl = properties.getPublicUrl().replaceAll("/+$", "");
        if (properties.isEnabled() && features.refreshTokens()) {
            add("google", CommonOAuth2Provider.GOOGLE, properties.getGoogleClientId(), properties.getGoogleClientSecret());
            add("github", CommonOAuth2Provider.GITHUB, properties.getGithubClientId(), properties.getGithubClientSecret());
        }
    }

    private void add(String id, CommonOAuth2Provider provider, String clientId, String secret) {
        if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) return;
        ClientRegistration.Builder builder = provider.getBuilder(id).clientId(clientId).clientSecret(secret)
                .redirectUri(publicUrl + "/api/auth/oauth2/callback/" + id);
        if ("google".equals(id)) builder.scope("openid", "profile", "email");
        else builder.scope("read:user", "user:email");
        // Common provider endpoints are fixed; no remote discovery at startup.
        registrations.put(id, builder.build());
    }

    public Map<String, Boolean> availability() { return Map.of("google", enabled("google"), "github", enabled("github")); }
    public boolean enabled(String provider) { return registrations.containsKey(provider); }
    public boolean hasProviders() { return !registrations.isEmpty(); }
    public String publicUrl() { return publicUrl; }
    @Override public ClientRegistration findByRegistrationId(String id) { return registrations.get(id); }
    @Override public Iterator<ClientRegistration> iterator() { return registrations.values().iterator(); }
}

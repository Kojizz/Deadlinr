package com.deadlinr.app.security.oauth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestOperations rest;

    public GitHubUserService(RestOperations rest) {
        this.rest = rest;
        delegate.setRestOperations(rest);
    }

    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User user = delegate.loadUser(request);
        if (!"github".equals(request.getClientRegistration().getRegistrationId())) throw OAuthProfile.error("failed");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(request.getAccessToken().getTokenValue());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            var emails = rest.exchange("https://api.github.com/user/emails", HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
            String verifiedEmail = emails == null ? null : emails.stream()
                    .filter(email -> Boolean.TRUE.equals(email.get("primary")) && Boolean.TRUE.equals(email.get("verified")))
                    .map(email -> email.get("email")).filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
            Map<String, Object> attributes = new HashMap<>(user.getAttributes());
            attributes.put("deadlinr_email_verified", verifiedEmail != null);
            if (verifiedEmail != null) attributes.put("deadlinr_verified_email", verifiedEmail);
            return new DefaultOAuth2User(user.getAuthorities(), attributes, "id");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw OAuthProfile.error("failed");
        }
    }
}

package com.deadlinr.app.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthLoginConfig {
    public static final String ERROR_KEY = OAuthLoginConfig.class.getName() + ".error";
    private final OAuthProviderRegistry providers;
    private final OAuthHandshake handshake;
    private final OAuthFlowHandler handler;
    private final RestTemplate rest;

    public OAuthLoginConfig(OAuthProviderRegistry providers, OAuthHandshake handshake, OAuthFlowHandler handler,
                            @Qualifier("oauthRestOperations") RestTemplate rest) {
        this.providers = providers; this.handshake = handshake; this.handler = handler; this.rest = rest;
    }

    public void configure(HttpSecurity http) throws Exception {
        if (!providers.hasProviders()) return;
        var tokenClient = new DefaultAuthorizationCodeTokenResponseClient();
        tokenClient.setRestOperations(rest);
        var userService = new DefaultOAuth2UserService();
        userService.setRestOperations(rest);
        var oidc = new OidcUserService();
        oidc.setOauth2UserService(userService);
        var delegate = new DefaultOAuth2AuthorizationRequestResolver(providers, "/api/auth/oauth2/authorization");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        OAuth2AuthorizationRequestResolver resolver = new OAuth2AuthorizationRequestResolver() {
            @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                String prefix = "/api/auth/oauth2/authorization/";
                if (!request.getRequestURI().startsWith(prefix)) return null;
                String provider = request.getRequestURI().substring(prefix.length());
                return providers.enabled(provider) ? resolve(request, provider) : null;
            }
            @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String provider) {
                if (!providers.enabled(provider)) return null;
                try { return handshake.bindLink(request, provider, delegate.resolve(request, provider)); }
                catch (OAuth2AuthenticationException ex) { request.setAttribute(ERROR_KEY, ex); return null; }
            }
        };
        http.oauth2Login(oauth -> oauth
                .clientRegistrationRepository(providers)
                .authorizedClientRepository(new OAuth2AuthorizedClientRepository() {
                    @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication principal, HttpServletRequest request) { return null; }
                    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {}
                    @Override public void removeAuthorizedClient(String id, Authentication principal, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {}
                })
                .loginPage(providers.publicUrl() + "/login")
                .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/authorization")
                        .authorizationRequestResolver(resolver)
                        .authorizationRequestRepository(new TimedAuthorizationRequestRepository(Clock.systemUTC())))
                .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/callback/*"))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient))
                .userInfoEndpoint(endpoint -> endpoint.userService(new GitHubUserService(rest)).oidcUserService(oidc))
                .successHandler(handler).failureHandler(handler));
    }

    @Bean
    public JwtDecoderFactory<ClientRegistration> oauthJwtDecoderFactory() {
        Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
        return registration -> decoders.computeIfAbsent(registration.getRegistrationId(), key -> {
            var decoder = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(rest).build();
            decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), new OidcIdTokenValidator(registration)));
            return decoder;
        });
    }
}

package com.deadlinr.app.security.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record OAuthProfile(String subject, String email, boolean verified, String fullName) {
    public static OAuthProfile from(String provider, OAuth2User user) {
        if ("google".equals(provider) && user instanceof OidcUser oidc) {
            // Trust validated ID-token claims, not an unvalidated client payload.
            var claims = oidc.getIdToken().getClaims();
            return new OAuthProfile(oidc.getSubject(), (String) claims.get("email"), Boolean.TRUE.equals(claims.get("email_verified")), (String) claims.get("name"));
        }
        if ("github".equals(provider)) {
            Object id = user.getAttribute("id");
            if (!(id instanceof Number number) || number.longValue() <= 0) throw error("failed");
            return new OAuthProfile(Long.toString(number.longValue()), user.getAttribute("deadlinr_verified_email"),
                    Boolean.TRUE.equals(user.getAttribute("deadlinr_email_verified")), user.getAttribute("name"));
        }
        throw error("failed");
    }

    public static OAuth2AuthenticationException error(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}

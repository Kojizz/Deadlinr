package com.deadlinr.app.security.oauth;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

final class OAuthHttp {
    private OAuthHttp() {}

    static RestTemplate create() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        var rest = new RestTemplate(factory);
        // The token converter must precede Jackson's generic object converter.
        rest.getMessageConverters().add(0, new OAuth2AccessTokenResponseHttpMessageConverter());
        rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        return rest;
    }
}

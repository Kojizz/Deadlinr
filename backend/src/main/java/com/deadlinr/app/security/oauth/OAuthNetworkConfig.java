package com.deadlinr.app.security.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OAuthNetworkConfig {
    @Bean
    public RestTemplate oauthRestOperations() { return OAuthHttp.create(); }
}

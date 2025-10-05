package com.deadlinr.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.oauth")
@Getter
@Setter
public class OAuthProperties {
    private boolean enabled;
    private String publicUrl = "http://localhost:3000";
    private String googleClientId = "";
    private String googleClientSecret = "";
    private String githubClientId = "";
    private String githubClientSecret = "";
}

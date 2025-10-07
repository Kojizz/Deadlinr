package com.deadlinr.app.config;

import com.deadlinr.app.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the Phase-10 feature flags actually turn features off:
 * legacy bearer-token auth, no rate limiting, no API docs, no Prometheus.
 */
@SpringBootTest(properties = {
        "app.features.refresh-tokens=false",
        "app.features.login-rate-limit=false",
        "app.features.api-docs=false",
        "app.features.observability=false"
})
@AutoConfigureMockMvc
@Testcontainers
class FeatureFlagsDisabledTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.reset();
    }

    @Test
    void legacyMode_returnsTokenInBody_andNoCookies() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alice\",\"email\":\"alice@test.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getCookies()).isEmpty();

        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.com"));
    }

    @Test
    void refreshEndpoint_isGone() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noRateLimiting() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@test.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void apiDocs_andPrometheus_areHidden() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
    }
}

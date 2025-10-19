package com.deadlinr.app.security;

import com.deadlinr.app.repository.RefreshTokenRepository;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.service.RateLimitService;
import jakarta.servlet.http.Cookie;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RefreshTokenFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.reset();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void noOauthCredentialsKeepsLocalLoginAvailableWithoutCreatingAnOauthSession() throws Exception {
        MvcResult options = mockMvc.perform(get("/api/auth/oauth2/providers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.google").value(false)).andExpect(jsonPath("$.github").value(false)).andReturn();
        assertThat(options.getRequest().getSession(false)).isNull();
        mockMvc.perform(get("/api/auth/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
        MvcResult local = register();
        mockMvc.perform(get("/api/users/me").cookie(local.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE)))
                .andExpect(status().isOk());
    }

    @Test
    void register_setsHttpOnlyCookies_andMeWorks() throws Exception {
        MvcResult result = register();

        Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        Cookie refresh = result.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);

        assertThat(access).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getMaxAge()).isEqualTo(900);
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"token\"");

        mockMvc.perform(get("/api/users/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.com"));
    }

    @Test
    void refresh_rotatesToken_andReusingOldTokenIsRejected() throws Exception {
        MvcResult register = register();
        Cookie access = register.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        Cookie refresh = register.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);

        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("alice@test.com"))
                .andReturn();

        Cookie newRefresh = rotated.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);
        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh.getValue()).isNotEqualTo(refresh.getValue());

        // The old access token is still valid (epoch untouched by normal rotation).
        mockMvc.perform(get("/api/users/me").cookie(access)).andExpect(status().isOk());

        // Presenting the already-rotated token is detected as reuse.
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected; session revoked"));

        // The whole family must be revoked.
        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void reuseDetection_killsOutstandingAccessTokens() throws Exception {
        MvcResult register = register();
        Cookie access0 = register.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        Cookie refresh0 = register.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);

        MvcResult firstRotation = mockMvc.perform(post("/api/auth/refresh").cookie(refresh0))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access1 = firstRotation.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        Cookie refresh1 = firstRotation.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);
        assertThat(refresh1).isNotNull();

        // Presenting the already-rotated token again is detected as reuse.
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh0))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected; session revoked"));

        // auth_epoch was bumped: every access token (old and new) is now dead.
        mockMvc.perform(get("/api/users/me").cookie(access0)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").cookie(access1)).andExpect(status().isUnauthorized());

        // The replacement refresh token of the compromised family is dead too.
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh1)).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        MvcResult register = register();
        Cookie refresh = register.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);

        mockMvc.perform(post("/api/auth/logout").cookie(refresh))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAll_invalidatesEverything() throws Exception {
        MvcResult register = register();
        Cookie access = register.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        Cookie refresh = register.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);

        mockMvc.perform(post("/api/auth/logout-all").cookie(access))
                .andExpect(status().isNoContent());

        // Access token killed instantly by the epoch bump...
        mockMvc.perform(get("/api/users/me").cookie(access)).andExpect(status().isUnauthorized());
        // ...and refresh tokens all revoked.
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void requestId_isEchoedOrGenerated() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "trace-123"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER))
                        .isEqualTo("trace-123"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER))
                        .isNotBlank());
    }

    private MvcResult register() throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alice\",\"email\":\"alice@test.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }
}

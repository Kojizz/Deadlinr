package com.deadlinr.app.security;

import com.deadlinr.app.repository.OAuthIdentityRepository;
import com.deadlinr.app.repository.RefreshTokenRepository;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.security.oauth.OAuthProviderRegistry;
import com.deadlinr.app.service.RateLimitService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.oauth.enabled=true", "app.oauth.google-client-id=test-google", "app.oauth.google-client-secret=test-google-secret",
        "app.oauth.github-client-id=test-github", "app.oauth.github-client-secret=test-github-secret"
})
@AutoConfigureMockMvc
@Testcontainers
class OAuthFlowTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static final RSAKey signingKey;
    static { try { signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired OAuthIdentityRepository identities;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired RateLimitService rateLimits;
    @Autowired OAuthProviderRegistry providers;
    @Autowired @Qualifier("oauthRestOperations") RestTemplate rest;
    MockRestServiceServer provider;

    @BeforeEach void setup() {
        identities.deleteAll(); refreshTokens.deleteAll(); users.deleteAll(); rateLimits.reset();
        provider = MockRestServiceServer.bindTo(rest).ignoreExpectOrder(true).build();
    }

    @Test void availabilityOnlyExposesFlags_andStartUsesFixedCallbackPkceNonceAndState() throws Exception {
        mvc.perform(get("/api/auth/oauth2/providers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.google").value(true)).andExpect(jsonPath("$.github").value(true))
                .andExpect(header().string("Cache-Control", "no-store"));
        Flow flow = start("google");
        assertThat(flow.query().get("redirect_uri")).isEqualTo("http://localhost:3000/api/auth/oauth2/callback/google");
        assertThat(flow.query().get("code_challenge_method")).isEqualTo("S256");
        assertThat(flow.query().get("state")).isNotBlank(); assertThat(flow.query().get("nonce")).isNotBlank();
        mvc.perform(get("/api/users/me").session(flow.session())).andExpect(status().isUnauthorized());
        provider.verify();
    }

    @Test void googleLoginIssuesOnlyLocalCookies_andRepeatLoginKeepsTheSameOwner() throws Exception {
        Flow flow = start("google"); google(flow, "valid", true, "student@example.test");
        MvcResult result = callback("google", flow);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
        Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        assertThat(access).isNotNull(); assertThat(access.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE)).isNotNull();
        assertThat(flow.session().isInvalid()).isTrue();
        mvc.perform(get("/api/users/me").cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("student@example.test"));
        Long id = users.findAll().getFirst().getId();
        assertThat(users.findAll().getFirst().isPasswordLoginEnabled()).isFalse();
        provider.verify(); provider.reset();
        Flow again = start("google"); google(again, "valid", true, "changed@example.test"); callback("google", again);
        assertThat(users.count()).isEqualTo(1); assertThat(identities.count()).isEqualTo(1);
        assertThat(users.findAll().getFirst().getId()).isEqualTo(id);
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("student@example.test");
        provider.verify();
    }

    @Test void githubPrivateEmailIsReadFromTheVerifiedPrimaryEmailEndpoint() throws Exception {
        Flow flow = start("github"); github(true, 42);
        MvcResult result = callback("github", flow);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
        assertThat(identities.findAll().getFirst().getSubject()).isEqualTo("42");
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("student@example.test");
        provider.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"nonce", "issuer", "audience", "expired", "signature"})
    void invalidGoogleIdTokensCannotCreateAWorkspace(String failure) throws Exception {
        Flow flow = start("google"); google(flow, failure, true, "student@example.test");
        MvcResult result = callback("google", flow);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?oauthError=failed");
        assertThat(users.count()).isZero(); assertThat(identities.count()).isZero();
        assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE)).isNull();
        provider.verify();
    }

    @Test void unverifiedProviderEmailsCannotCreateUsers() throws Exception {
        Flow flow = start("github"); github(false, 42);
        assertThat(callback("github", flow).getResponse().getRedirectedUrl()).endsWith("oauthError=email_unverified");
        assertThat(users.count()).isZero(); provider.verify(); provider.reset();
        flow = start("google"); google(flow, "valid", false, "student@example.test");
        assertThat(callback("google", flow).getResponse().getRedirectedUrl()).endsWith("oauthError=email_unverified");
        assertThat(users.count()).isZero(); provider.verify();
    }

    @Test void matchingEmailDoesNotAutomaticallyLinkOrChangeExistingPasswords() throws Exception {
        MvcResult local = register("Student@Example.Test");
        String hash = users.findAll().getFirst().getPassword();
        Flow flow = start("github"); github(true, 42);
        assertThat(callback("github", flow).getResponse().getRedirectedUrl()).endsWith("oauthError=email_conflict");
        assertThat(users.count()).isEqualTo(1); assertThat(identities.count()).isZero();
        assertThat(users.findAll().getFirst().getPassword()).isEqualTo(hash);
        mvc.perform(get("/api/users/me").cookie(local.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE))).andExpect(status().isOk());
        provider.verify();
    }

    @Test void explicitLinkRequiresSameOriginAndSession_andKeepsOwnership() throws Exception {
        MvcResult local = register("local@example.test");
        Cookie cookie = local.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        Long owner = users.findAll().getFirst().getId();
        mvc.perform(post("/api/auth/oauth2/accounts/github/link").cookie(cookie).header("Origin", "https://other.example")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/oauth2/accounts/github/link").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        MvcResult link = mvc.perform(post("/api/auth/oauth2/accounts/github/link").cookie(cookie).header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn();
        String ticket = new com.fasterxml.jackson.databind.ObjectMapper().readTree(link.getResponse().getContentAsString()).get("ticket").asText();
        Flow flow = start("github", (MockHttpSession) link.getRequest().getSession(), cookie, ticket);
        github(true, 42);
        MvcResult linked = callback("github", flow, cookie);
        assertThat(linked.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/settings?oauthConnected=github");
        assertThat(users.count()).isEqualTo(1); assertThat(identities.findAll().getFirst().getUser().getId()).isEqualTo(owner);
        provider.verify(); provider.reset();
        Flow login = start("github"); github(true, 42);
        MvcResult signedIn = callback("github", login);
        mvc.perform(get("/api/users/me").cookie(signedIn.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner));
        Cookie connectedCookie = signedIn.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        mvc.perform(get("/api/auth/oauth2/accounts").cookie(connectedCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.linked.github").value(true)).andExpect(jsonPath("$.passwordLoginEnabled").value(true));
        mvc.perform(delete("/api/auth/oauth2/accounts/github").cookie(connectedCookie).header("Origin", "http://localhost:3000"))
                .andExpect(status().isNoContent());
        assertThat(identities.count()).isZero(); assertThat(users.count()).isEqualTo(1);
        assertThat(users.findAll().getFirst().getId()).isEqualTo(owner);
        provider.verify();
    }

    @Test void missingWrongReplayedStateAndCancellationDoNotCallProviders() throws Exception {
        Flow flow = start("github");
        mvc.perform(get("/api/auth/oauth2/callback/github").session(flow.session()).param("code", "test-code").param("state", "wrong"))
                .andExpect(redirectedUrl("http://localhost:3000/login?oauthError=expired"));
        mvc.perform(get("/api/auth/oauth2/callback/github").param("code", "test-code").param("state", flow.query().get("state")))
                .andExpect(redirectedUrl("http://localhost:3000/login?oauthError=expired"));
        flow = start("github");
        mvc.perform(get("/api/auth/oauth2/callback/github").session(flow.session()).param("error", "access_denied").param("state", flow.query().get("state")))
                .andExpect(redirectedUrl("http://localhost:3000/login?oauthError=cancelled"));
        assertThat(users.count()).isZero(); provider.verify();
    }

    @Test void aProviderOnlyAccountCannotDisconnectItsLastSignInMethod() throws Exception {
        Flow flow = start("github"); github(true, 42);
        Cookie cookie = callback("github", flow).getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        mvc.perform(delete("/api/auth/oauth2/accounts/github").cookie(cookie).header("Origin", "http://localhost:3000"))
                .andExpect(status().isBadRequest());
        assertThat(identities.count()).isEqualTo(1); provider.verify();
    }

    @Test void anIdentityCannotBeLinkedToASecondWorkspace() throws Exception {
        Flow first = start("github"); github(true, 42); callback("github", first);
        Long original = users.findByEmail("student@example.test").orElseThrow().getId();
        provider.verify(); provider.reset();
        MvcResult local = register("second@example.test");
        Cookie cookie = local.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        MvcResult link = mvc.perform(post("/api/auth/oauth2/accounts/github/link").cookie(cookie).header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn();
        String ticket = new com.fasterxml.jackson.databind.ObjectMapper().readTree(link.getResponse().getContentAsString()).get("ticket").asText();
        Flow flow = start("github", (MockHttpSession) link.getRequest().getSession(), cookie, ticket);
        github(true, 42);
        assertThat(callback("github", flow, cookie).getResponse().getRedirectedUrl()).endsWith("oauthError=identity_conflict");
        assertThat(identities.count()).isEqualTo(1);
        assertThat(identities.findAll().getFirst().getUser().getId()).isEqualTo(original);
        provider.verify();
    }

    @Test void logoutEverywhereDuringLinkingRejectsTheCallback() throws Exception {
        MvcResult local = register("local@example.test");
        Cookie cookie = local.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        MvcResult link = mvc.perform(post("/api/auth/oauth2/accounts/github/link").cookie(cookie).header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn();
        String ticket = new com.fasterxml.jackson.databind.ObjectMapper().readTree(link.getResponse().getContentAsString()).get("ticket").asText();
        Flow flow = start("github", (MockHttpSession) link.getRequest().getSession(), cookie, ticket);
        mvc.perform(post("/api/auth/logout-all").cookie(cookie)).andExpect(status().isNoContent());
        github(true, 42);
        assertThat(callback("github", flow, cookie).getResponse().getRedirectedUrl()).endsWith("oauthError=session_changed");
        assertThat(identities.count()).isZero(); provider.verify();
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Local Student\",\"email\":\"" + email + "\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isCreated()).andReturn();
    }
    private Flow start(String id) throws Exception { return start(id, null, null, null); }
    private Flow start(String id, MockHttpSession session, Cookie cookie, String ticket) throws Exception {
        var request = get("/api/auth/oauth2/authorization/" + id).header("Host", "untrusted.example");
        if (session != null) request.session(session);
        if (cookie != null) request.cookie(cookie);
        if (ticket != null) request.param("link", ticket);
        MvcResult result = mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
        var params = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build().getQueryParams().toSingleValueMap()
                .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> java.net.URLDecoder.decode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8)));
        return new Flow((MockHttpSession) result.getRequest().getSession(), params);
    }
    private MvcResult callback(String id, Flow flow, Cookie... cookies) throws Exception {
        var request = get("/api/auth/oauth2/callback/" + id).session(flow.session())
                .param("code", "test-code").param("state", flow.query().get("state"));
        if (cookies.length > 0) request.cookie(cookies);
        return mvc.perform(request)
                .andExpect(status().is3xxRedirection()).andReturn();
    }
    private void github(boolean verified, long id) {
        var registration = providers.findByRegistrationId("github");
        provider.expect(requestTo(registration.getProviderDetails().getTokenUri())).andRespond(withSuccess("{\"access_token\":\"test-github-token\",\"token_type\":\"bearer\",\"scope\":\"read:user user:email\"}", MediaType.APPLICATION_JSON));
        provider.expect(requestTo(registration.getProviderDetails().getUserInfoEndpoint().getUri())).andRespond(withSuccess("{\"id\":" + id + ",\"name\":\"Test Student\",\"email\":null}", MediaType.APPLICATION_JSON));
        provider.expect(requestTo("https://api.github.com/user/emails")).andRespond(withSuccess("[{\"email\":\"student@example.test\",\"primary\":true,\"verified\":" + verified + "}]", MediaType.APPLICATION_JSON));
    }
    private void google(Flow flow, String failure, boolean verified, String email) throws Exception {
        var registration = providers.findByRegistrationId("google");
        String nonce = java.net.URLDecoder.decode(flow.query().get("nonce"), java.nio.charset.StandardCharsets.UTF_8);
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject("google-stable-id").issuer(failure.equals("issuer") ? "https://other.example" : "https://accounts.google.com")
                .audience(failure.equals("audience") ? "another-app" : "test-google").issueTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(failure.equals("expired") ? now.minusSeconds(3600) : now.plusSeconds(3600)))
                .claim("nonce", failure.equals("nonce") ? "wrong-nonce" : nonce).claim("email", email).claim("email_verified", verified).claim("name", "Test Student").build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner(failure.equals("signature") ? new RSAKeyGenerator(2048).generate() : signingKey));
        provider.expect(requestTo(registration.getProviderDetails().getTokenUri())).andRespond(withSuccess("{\"access_token\":\"test-google-token\",\"token_type\":\"bearer\",\"expires_in\":3600,\"id_token\":\"" + jwt.serialize() + "\"}", MediaType.APPLICATION_JSON));
        provider.expect(ExpectedCount.between(0, Integer.MAX_VALUE), requestTo(registration.getProviderDetails().getJwkSetUri()))
                .andRespond(withSuccess("{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}", MediaType.APPLICATION_JSON));
        provider.expect(ExpectedCount.between(0, 1), requestTo(registration.getProviderDetails().getUserInfoEndpoint().getUri()))
                .andRespond(withSuccess("{\"sub\":\"google-stable-id\",\"name\":\"Test Student\",\"email\":\"" + email + "\",\"email_verified\":true}", MediaType.APPLICATION_JSON));
    }
    private record Flow(MockHttpSession session, Map<String, String> query) {}
}

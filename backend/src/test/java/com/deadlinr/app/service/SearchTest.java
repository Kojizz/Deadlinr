package com.deadlinr.app.service;

import com.deadlinr.app.entity.Page;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.repository.PageRepository;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.security.AuthCookieService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SearchTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitService rateLimitService;

    private User alice;
    private Cookie aliceAccess;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitService.reset();
        pageRepository.deleteAll();
        userRepository.deleteAll();

        aliceAccess = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alice\",\"email\":\"alice@search.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);

        alice = userRepository.findByEmail("alice@search.test").orElseThrow();
    }

    private Page page(User owner, String title, String contentText) throws Exception {
        JsonNode content = contentText == null ? null : objectMapper.readTree(
                "[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"" + contentText + "\",\"styles\":{}}]}]");
        return pageRepository.save(Page.builder()
                .owner(owner)
                .title(title)
                .favorite(false)
                .position(1.0)
                .content(content)
                .searchText(contentText == null ? "" : contentText)
                .build());
    }

    @Test
    void findsByPartialTitle() throws Exception {
        page(alice, "Machine Learning notes", null);

        mockMvc.perform(get("/api/search").param("q", "mach").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Machine Learning notes"))
                .andExpect(jsonPath("$[0].type").value("PAGE"));
    }

    @Test
    void findsByContent() throws Exception {
        page(alice, "CS notes", "gradient descent is everywhere");

        mockMvc.perform(get("/api/search").param("q", "descent").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("CS notes"));
    }

    @Test
    void findsCyrillicByCyrillicQuery() throws Exception {
        page(alice, "Конспект по матанализу", null);

        mockMvc.perform(get("/api/search").param("q", "матанал").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Конспект по матанализу"));
    }

    @Test
    void cannotSeeAnotherUsersPages() throws Exception {
        page(alice, "Alice secret plan", null);

        Cookie bobAccess = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Bob\",\"email\":\"bob@search.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);

        mockMvc.perform(get("/api/search").param("q", "secret").cookie(bobAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void excludesArchivedPages() throws Exception {
        Page archived = page(alice, "Archived notes", null);
        archived.setArchivedAt(LocalDateTime.now());
        pageRepository.save(archived);

        mockMvc.perform(get("/api/search").param("q", "Archived").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void blankQueryReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   ").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void toleratesOneLetterTypo() throws Exception {
        page(alice, "Machine Learning notes", null);
        mockMvc.perform(get("/api/search").param("q", "machime").cookie(aliceAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Machine Learning notes"));
    }

    @Test
    void indexesCreateRenameAndContentThroughTheApi() throws Exception {
        String body = mockMvc.perform(post("/api/pages").cookie(aliceAccess)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Fresh indexing\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();
        mockMvc.perform(get("/api/search").param("q", "Fresh").cookie(aliceAccess))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(patch("/api/pages/" + id).cookie(aliceAccess)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed indexing\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("q", "Renamed").cookie(aliceAccess))
                .andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(get("/api/search").param("q", "Fresh").cookie(aliceAccess))
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(put("/api/pages/" + id + "/content").cookie(aliceAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":[{\"type\":\"paragraph\",\"content\":[{\"text\":\"quasiparticle discovery\"}]}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("q", "quasipart").cookie(aliceAccess))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].snippet").value(org.hamcrest.Matchers.containsString("quasiparticle")));
    }

    @Test
    void supportsAccentsKazakhAndWebSearchOperators() throws Exception {
        page(alice, "Café Қазақстан", null);
        page(alice, "Machine Learning notes", null);
        page(alice, "Machine secret notes", null);
        for (String query : new String[]{"cafe", "қазақ"}) {
            mockMvc.perform(get("/api/search").param("q", query).cookie(aliceAccess))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].title").value("Café Қазақстан"));
        }
        mockMvc.perform(get("/api/search").param("q", "mach -secret").cookie(aliceAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Machine Learning notes"));
        mockMvc.perform(get("/api/search").param("q", "\"Machine Learning\"").cookie(aliceAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/search").param("q", "cafe OR learning").cookie(aliceAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void punctuationIsNotSqlOrTsquerySyntaxAndResultsRequireAuth() throws Exception {
        page(alice, "Private page", null);
        for (String query : new String[]{"%", "_", "'", ":* & | !", "\""}) {
            mockMvc.perform(get("/api/search").param("q", query).cookie(aliceAccess))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        }
        mockMvc.perform(get("/api/search").param("q", "Private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fuzzyMatchesAreAlsoOwnerScoped() throws Exception {
        page(alice, "Machine Learning notes", null);
        Cookie bobAccess = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Bob\",\"email\":\"bob@search.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getCookie(AuthCookieService.ACCESS_COOKIE);
        mockMvc.perform(get("/api/search").param("q", "machime").cookie(bobAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
}

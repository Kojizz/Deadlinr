package com.deadlinr.app.config;

import com.deadlinr.app.entity.Role;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WebCacheTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;

    @Test
    void jsonGetSendsEtagAndRevalidatesWith304() throws Exception {
        User u = users.save(User.builder().fullName("Cache").email(UUID.randomUUID() + "@test")
                .password("unused").role(Role.STUDENT).build());
        String etag = mockMvc.perform(get("/api/labels").with(user(u)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("W/\"")))
                .andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/labels").with(user(u)).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }
}

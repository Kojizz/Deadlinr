package com.deadlinr.app.service;

import com.deadlinr.app.dto.PageRequest;
import com.deadlinr.app.entity.Role;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Measures the existing tree implementation; does not change application behavior. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
class G0PersistenceAuditTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PageService pages;
    @Autowired UserRepository users;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ObjectMapper json;

    @Test
    void recordTreeQueryAndEntityLoadCounts() throws Exception {
        var owner = users.save(User.builder().fullName("G0 Audit").email("g0-db@example.test")
                .password("unused-test-password").role(Role.STUDENT).build());
        var root = pages.createPage(owner, new PageRequest("Root", null, null, null, null, null));
        for (int i = 0; i < 24; i++) {
            pages.createPage(owner, new PageRequest("Child " + i, null, root.id(), null, null, null));
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var tree = pages.getTree(owner);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).hasSize(24);
        var evidence = Map.of("nodes", 25, "preparedStatements", statistics.getPrepareStatementCount(),
                "entitiesLoaded", statistics.getEntityLoadCount(), "entityFetches", statistics.getEntityFetchCount(),
                "collectionFetches", statistics.getCollectionFetchCount(),
                "scope", "One root and 24 children, owner only, PostgreSQL 16 Testcontainers, cold service call");
        var output = Path.of(System.getProperty("g0.audit.output", "target/g0-persistence.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n");
    }
}

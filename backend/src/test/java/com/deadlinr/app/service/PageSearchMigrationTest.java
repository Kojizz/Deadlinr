package com.deadlinr.app.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PageSearchMigrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingRowsAndKeepsTheIndexFresh() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("2").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("""
                    insert into users (id, full_name, email, password, role, created_at)
                    values (1, 'Legacy', 'legacy@example.test', 'unused', 'STUDENT', now())
                    """);
            sql.execute("""
                    insert into pages (id, owner_id, title, content, search_text, favorite, position, created_at, updated_at)
                    values (1, 1, 'Machine Learning notes',
                            '[{"type":"paragraph","content":[{"text":"Café"}],"children":[{"content":[{"text":"quasiparticle"}]}]}]',
                            null, false, 1, now(), now())
                    """);
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
            try (var rows = sql.executeQuery("""
                    select search_text, search_title,
                           search_vector @@ deadlinr_prefix_query('mach') as title_hit,
                           search_vector @@ deadlinr_prefix_query('cafe quasipart') as content_hit
                    from pages where id = 1
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("search_text")).contains("Café", "quasiparticle");
                assertThat(rows.getString("search_title")).isEqualTo("machine learning notes");
                assertThat(rows.getBoolean("title_hit")).isTrue();
                assertThat(rows.getBoolean("content_hit")).isTrue();
            }
            sql.execute("update pages set title = 'Astronomy', content = '[{\"content\":[{\"text\":\"galaxy\"}]}]' where id = 1");
            try (var rows = sql.executeQuery("""
                    select search_vector @@ deadlinr_prefix_query('astro galax') as new_hit,
                           search_vector @@ deadlinr_prefix_query('machine') as old_hit
                    from pages where id = 1
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean("new_hit")).isTrue();
                assertThat(rows.getBoolean("old_hit")).isFalse();
            }
            try (var rows = sql.executeQuery("select count(*) from pg_indexes where indexname in ('idx_pages_search_title', 'idx_pages_search_vector')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}

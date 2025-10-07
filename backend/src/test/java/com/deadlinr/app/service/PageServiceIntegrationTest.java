package com.deadlinr.app.service;

import com.deadlinr.app.dto.PageContentRequest;
import com.deadlinr.app.dto.PageMoveRequest;
import com.deadlinr.app.dto.PageRequest;
import com.deadlinr.app.dto.PageResponse;
import com.deadlinr.app.dto.PageTreeNode;
import com.deadlinr.app.entity.Role;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ForbiddenException;
import com.deadlinr.app.repository.PageRepository;
import com.deadlinr.app.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PageServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PageService pageService;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        pageRepository.deleteAll();
        userRepository.deleteAll();
        alice = createUser("Alice", "alice@test.com");
        bob = createUser("Bob", "bob@test.com");
    }

    private User createUser(String fullName, String email) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .password("dummy-password")
                .role(Role.STUDENT)
                .build());
    }

    @Test
    void createPage_setsDefaults() {
        PageResponse page = pageService.createPage(alice, new PageRequest("My page", null, null, null, null, null));

        assertThat(page.id()).isNotNull();
        assertThat(page.title()).isEqualTo("My page");
        assertThat(page.favorite()).isFalse();
        assertThat(page.ownerId()).isEqualTo(alice.getId());
        assertThat(page.position()).isGreaterThan(0.0);
    }

    @Test
    void getTree_buildsNestedStructure() {
        PageResponse parent = pageService.createPage(alice, new PageRequest("Parent", null, null, null, null, null));
        pageService.createPage(alice, new PageRequest("Child", null, parent.id(), null, null, null));

        List<PageTreeNode> tree = pageService.getTree(alice);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).title()).isEqualTo("Parent");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).title()).isEqualTo("Child");
    }

    @Test
    void movePage_reparentsToRoot() {
        PageResponse parent = pageService.createPage(alice, new PageRequest("Parent", null, null, null, null, null));
        PageResponse child = pageService.createPage(alice, new PageRequest("Child", null, parent.id(), null, null, null));

        PageResponse moved = pageService.movePage(alice, child.id(), new PageMoveRequest(null, 5.0));

        assertThat(moved.parentId()).isNull();
        assertThat(moved.position()).isEqualTo(5.0);
    }

    @Test
    void movePage_preventsCycle() {
        PageResponse parent = pageService.createPage(alice, new PageRequest("Parent", null, null, null, null, null));
        PageResponse child = pageService.createPage(alice, new PageRequest("Child", null, parent.id(), null, null, null));

        assertThatThrownBy(() -> pageService.movePage(alice, parent.id(), new PageMoveRequest(child.id(), 1.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotAccessAnotherUsersPage() {
        PageResponse page = pageService.createPage(alice, new PageRequest("Alice page", null, null, null, null, null));

        assertThatThrownBy(() -> pageService.getPage(bob, page.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> pageService.movePage(bob, page.id(), new PageMoveRequest(null, 1.0)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> pageService.archive(bob, page.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void archiveAndRestore() {
        PageResponse page = pageService.createPage(alice, new PageRequest("Page", null, null, null, null, null));

        pageService.archive(alice, page.id());
        assertThat(pageService.getTrash(alice)).hasSize(1);
        assertThat(pageService.getTree(alice)).isEmpty();

        pageService.restore(alice, page.id());
        assertThat(pageService.getTree(alice)).hasSize(1);
        assertThat(pageService.getTrash(alice)).isEmpty();
    }

    @Test
    void updateContent_extractsSearchText() throws Exception {
        PageResponse page = pageService.createPage(alice, new PageRequest("Page", null, null, null, null, null));

        JsonNode content = objectMapper.readTree(
                "[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hello world\",\"styles\":{}}],\"children\":[]}]");
        PageResponse updated = pageService.updateContent(alice, page.id(), new PageContentRequest(content));

        assertThat(updated.content()).isNotNull();
        assertThat(pageRepository.findById(page.id()).orElseThrow().getSearchText()).isEqualTo("Hello world");
    }

    @Test
    void appendsRootAndChildPagesAndRejectsForeignParent() {
        PageResponse first = pageService.createPage(alice, new PageRequest("First", null, null, null, null, null));
        PageResponse second = pageService.createPage(alice, new PageRequest("Second", null, null, null, null, null));
        PageResponse child = pageService.createPage(alice, new PageRequest("Child", null, first.id(), null, null, null));
        PageResponse sibling = pageService.createPage(alice, new PageRequest("Sibling", null, first.id(), null, null, null));
        assertThat(second.position()).isGreaterThan(first.position());
        assertThat(sibling.position()).isGreaterThan(child.position());
        assertThat(pageService.getTree(alice)).extracting(PageTreeNode::id).containsExactly(first.id(), second.id());
        assertThat(pageService.getTree(alice).get(0).children()).extracting(PageTreeNode::id)
                .containsExactly(child.id(), sibling.id());
        assertThatThrownBy(() -> pageService.createPage(bob,
                new PageRequest("Forbidden", null, first.id(), null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }
}

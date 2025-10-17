package com.deadlinr.app.controller;

import com.deadlinr.app.dto.PageContentRequest;
import com.deadlinr.app.dto.PageMoveRequest;
import com.deadlinr.app.dto.PageRequest;
import com.deadlinr.app.dto.PageResponse;
import com.deadlinr.app.dto.PageTreeNode;
import com.deadlinr.app.dto.PageUpdateRequest;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.PageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pages")
@SecurityRequirement(name = "bearerAuth")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping("/tree")
    public ResponseEntity<List<PageTreeNode>> tree(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(pageService.getTree(user));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<PageResponse>> favorites(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(pageService.getFavorites(user));
    }

    @GetMapping("/trash")
    public ResponseEntity<List<PageResponse>> trash(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(pageService.getTrash(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PageResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(pageService.getPage(user, id));
    }

    @PostMapping
    public ResponseEntity<PageResponse> create(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody PageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pageService.createPage(user, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PageResponse> update(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @Valid @RequestBody PageUpdateRequest request) {
        return ResponseEntity.ok(pageService.updatePage(user, id, request));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<PageResponse> move(@AuthenticationPrincipal User user,
                                             @PathVariable Long id,
                                             @RequestBody PageMoveRequest request) {
        return ResponseEntity.ok(pageService.movePage(user, id, request));
    }

    @PutMapping("/{id}/content")
    public ResponseEntity<PageResponse> content(@AuthenticationPrincipal User user,
                                                @PathVariable Long id,
                                                @RequestBody PageContentRequest request) {
        return ResponseEntity.ok(pageService.updateContent(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal User user, @PathVariable Long id) {
        pageService.archive(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<PageResponse> restore(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(pageService.restore(user, id));
    }
}

package com.deadlinr.app.controller;

import com.deadlinr.app.dto.SearchResult;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.PageService;
import com.deadlinr.app.service.WorkService;
import java.util.stream.Stream;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final PageService pageService;
    private final WorkService workService;

    public SearchController(PageService pageService, WorkService workService) {
        this.workService=workService;
        this.pageService = pageService;
    }

    @GetMapping
    public ResponseEntity<List<SearchResult>> search(@AuthenticationPrincipal User user,
                                                     @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(Stream.concat(pageService.search(user, q).stream(), workService.search(user,q).stream()).toList());
    }
}

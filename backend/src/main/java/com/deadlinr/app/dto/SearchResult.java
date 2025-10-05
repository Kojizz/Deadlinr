package com.deadlinr.app.dto;

import com.deadlinr.app.entity.Page;
import io.swagger.v3.oas.annotations.media.Schema;

/** A single hit in the workspace-wide search (pages and work items). */
public record SearchResult(
        Long id,
        String title,
        @Schema(nullable = true) String icon,
        String type,
        @Schema(nullable = true) String snippet
) {
    public static final String TYPE_PAGE = "PAGE";

    public static SearchResult from(Page page) {
        String text = page.getSearchText();
        String snippet = text == null || text.isBlank()
                ? null
                : text.substring(0, Math.min(120, text.length()));
        return new SearchResult(page.getId(), page.getTitle(), page.getIcon(), TYPE_PAGE, snippet);
    }
}


package com.deadlinr.app.dto;

import com.deadlinr.app.entity.Page;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PageResponse(
        Long id,
        Long ownerId,
        @Schema(nullable = true) Long courseId,
        @Schema(nullable = true) Long parentId,
        String title,
        @Schema(nullable = true) String icon,
        @Schema(nullable = true) String coverStyle,
        @Schema(nullable = true) JsonNode content,
        boolean favorite,
        double position,
        @Schema(nullable = true) LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PageResponse from(Page page) {
        return new PageResponse(
                page.getId(),
                page.getOwner().getId(),
                page.getCourse() != null ? page.getCourse().getId() : null,
                page.getParent() != null ? page.getParent().getId() : null,
                page.getTitle(),
                page.getIcon(),
                page.getCoverStyle(),
                page.getContent(),
                page.isFavorite(),
                page.getPosition(),
                page.getArchivedAt(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }
}

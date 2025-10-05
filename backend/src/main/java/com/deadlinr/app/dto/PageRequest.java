package com.deadlinr.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PageRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Schema(nullable = true) Long courseId,

        @Schema(nullable = true) Long parentId,

        @Schema(nullable = true) String icon,

        @Schema(nullable = true) String coverStyle,

        @Schema(nullable = true) JsonNode content
) {
}

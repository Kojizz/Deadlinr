package com.deadlinr.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record PageUpdateRequest(
        @Schema(nullable = true)
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Schema(nullable = true) String icon,

        @Schema(nullable = true) String coverStyle,

        @Schema(nullable = true) Boolean favorite
) {
}

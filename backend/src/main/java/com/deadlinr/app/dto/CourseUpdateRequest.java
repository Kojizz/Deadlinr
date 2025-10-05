package com.deadlinr.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CourseUpdateRequest(
        @Schema(nullable = true)
        @Size(min = 1, max = 255, message = "Course name must be between 1 and 255 characters")
        String name,

        @Schema(nullable = true)
        @Size(max = 50, message = "Course code must be at most 50 characters")
        String code,

        @Schema(nullable = true) String instructor
) {
}

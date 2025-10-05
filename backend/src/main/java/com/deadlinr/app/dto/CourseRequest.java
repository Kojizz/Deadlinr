package com.deadlinr.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseRequest(
        @NotBlank(message = "Course name is required")
        @Size(max = 255, message = "Course name must be at most 255 characters")
        String name,

        @NotBlank(message = "Course code is required")
        @Size(max = 50, message = "Course code must be at most 50 characters")
        String code,

        @Schema(nullable = true) String instructor
) {
}

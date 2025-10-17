package com.deadlinr.app.dto;

import com.deadlinr.app.entity.Course;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        Long userId,
        String name,
        String code,
        @Schema(nullable = true) String instructor,
        LocalDateTime createdAt
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getUser().getId(),
                course.getName(),
                course.getCode(),
                course.getInstructor(),
                course.getCreatedAt()
        );
    }
}

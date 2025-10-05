package com.deadlinr.app.dto;

import com.deadlinr.app.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        @Schema(nullable = true) String group,
        @Schema(type = "string", allowableValues = {"STUDENT", "ADMIN"}) String role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getGroup(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}

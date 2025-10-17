package com.deadlinr.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * In cookie mode only {@code user} is populated (tokens ride on httpOnly
 * cookies). In legacy mode (refresh-tokens feature flag off) the long-lived
 * access token is returned in {@code token}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        @Schema(nullable = true) String token,
        UserResponse user
) {
}

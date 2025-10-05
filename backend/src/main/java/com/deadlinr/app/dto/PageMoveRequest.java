package com.deadlinr.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PageMoveRequest(
        @Schema(nullable = true) Long parentId,

        double position
) {
}

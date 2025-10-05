package com.deadlinr.app.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PageContentRequest(
        JsonNode content
) {
}

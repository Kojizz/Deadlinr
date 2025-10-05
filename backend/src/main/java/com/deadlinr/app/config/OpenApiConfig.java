package com.deadlinr.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI metadata for springdoc. The docs are served at /v3/api-docs (JSON)
 * and /swagger-ui.html, and are the single source of truth for the generated
 * TypeScript types on the frontend (see ADR-0002). Gated by app.features.api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI deadlinrOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Deadlinr API")
                        .version("v1")
                        .description("Deadlinr university workspace API. " +
                                "Auth uses httpOnly cookies (access + rotating refresh) by default."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Legacy access token (only used when refresh-token cookies are disabled)")));
    }

    /**
     * Makes the generated client usable:
     * 1. Every property not explicitly marked @Schema(nullable = true) is
     *    added to the schema's "required" list, so openapi-typescript emits
     *    non-optional fields.
     * 2. springdoc drops the recursive "children" property of PageTreeNode;
     *    restore it as a self-referencing array.
     */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public OpenApiCustomizer deadlinrSchemaCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }
            components.getSchemas().forEach((name, schema) -> {
                Map properties = schema.getProperties();
                if (properties == null) {
                    return;
                }
                List<String> required = schema.getRequired() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(schema.getRequired());
                properties.forEach((propertyName, property) -> {
                    Schema<?> prop = (Schema<?>) property;
                    if (!Boolean.TRUE.equals(prop.getNullable()) && !required.contains(propertyName)) {
                        required.add((String) propertyName);
                    }
                });
                schema.setRequired(required);
            });

            Schema treeNode = components.getSchemas().get("PageTreeNode");
            if (treeNode != null && treeNode.getProperties() != null
                    && !treeNode.getProperties().containsKey("children")) {
                Schema<Object> childRef = new Schema<>();
                childRef.set$ref("#/components/schemas/PageTreeNode");
                Schema children = new Schema<>().type("array").items(childRef);
                treeNode.addProperty("children", children);
                if (treeNode.getRequired() == null) {
                    treeNode.setRequired(new ArrayList<>());
                }
                treeNode.getRequired().add("children");
            }
        };
    }
}

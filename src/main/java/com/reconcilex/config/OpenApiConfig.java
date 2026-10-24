package com.reconcilex.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReconcileX Payment Reconciliation Engine API")
                        .version("1.0.0")
                        .description("High-performance payment reconciliation engine featuring idempotent ingestion, multi-pass deterministic matching, auditable decision logging, and role-based JWT security.")
                        .contact(new Contact().name("ReconcileX Core Engineering Team")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Submit your signed HS256 JWT access token obtained from /api/auth/login as a Bearer token.")));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("1-auth-and-admin")
                .pathsToMatch("/api/auth/**", "/api/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi importsApi() {
        return GroupedOpenApi.builder()
                .group("2-file-imports")
                .pathsToMatch("/api/imports/**")
                .build();
    }

    @Bean
    public GroupedOpenApi reconciliationApi() {
        return GroupedOpenApi.builder()
                .group("3-reconciliation")
                .pathsToMatch("/api/reconciliation/**")
                .build();
    }

    @Bean
    public GroupedOpenApi auditApi() {
        return GroupedOpenApi.builder()
                .group("4-audit-events")
                .pathsToMatch("/api/audit-events/**")
                .build();
    }

    @Bean
    public GroupedOpenApi healthApi() {
        return GroupedOpenApi.builder()
                .group("5-system-health")
                .pathsToMatch("/api/health/**")
                .build();
    }
}

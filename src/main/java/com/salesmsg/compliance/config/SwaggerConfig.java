package com.salesmsg.compliance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * OpenAPI (Swagger) configuration for the 10DLC Compliance application.
 * Configures API documentation, authentication, and other metadata.
 */
@Configuration
public class SwaggerConfig {

    @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
    private String swaggerPath;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Configures the OpenAPI documentation.
     */
    @Bean
    public OpenAPI complianceOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers())
                .tags(apiTags())
                .components(apiComponents())
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    /**
     * API information including title, description, version, etc.
     */
    private Info apiInfo() {
        return new Info()
                .title("10DLC Compliance API")
                .description("API for AI-Driven Compliance for 10DLC Submissions")
                .version("0.1.0")
                .contact(new Contact()
                        .name("SalesMsg Support")
                        .email("support@salesmsg.com")
                        .url("https://salesmsg.com"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://salesmsg.com/terms"));
    }

    /**
     * Server configurations for different environments.
     */
    private List<Server> apiServers() {
        Server localServer = new Server()
                .url("http://localhost:" + serverPort)
                .description("Local Development Server");

        Server devServer = new Server()
                .url("https://compliance-api-dev.salesmsg.com")
                .description("Development Server");

        Server stagingServer = new Server()
                .url("https://compliance-api-staging.salesmsg.com")
                .description("Staging Server");

        Server prodServer = new Server()
                .url("https://compliance-api.salesmsg.com")
                .description("Production Server");

        return Arrays.asList(localServer, devServer, stagingServer, prodServer);
    }

    /**
     * API tags for categorizing endpoints.
     */
    private List<Tag> apiTags() {
        return Arrays.asList(
                new Tag().name("Content Generation").description("AI-powered compliant content generation"),
                new Tag().name("Message Validation").description("SMS message compliance validation"),
                new Tag().name("Image Analysis").description("Image compliance analysis"),
                new Tag().name("Website Validation").description("Website compliance validation"),
                new Tag().name("Submissions").description("Compliance submission operations"),
                new Tag().name("Verification").description("Compliance verification operations")
        );
    }

    /**
     * API components including security schemes.
     */
    private Components apiComponents() {
        return new Components()
                .addSecuritySchemes("basicAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic Authentication"));
    }
}
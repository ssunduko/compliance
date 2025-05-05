package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request model for Firecrawl extract API v1.
 * Based on the current API documentation at https://docs.firecrawl.dev/features/extract
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirecrawlExtractRequest {

    /**
     * List of URLs to extract data from.
     * Either urls or prompt must be provided.
     */
    private List<String> urls;

    /**
     * Prompt for extraction.
     * Either urls or prompt must be provided.
     */
    private String prompt;

    /**
     * The schema to extract data from the page.
     */
    private JsonNode schema;

    /**
     * The language model to use for extraction.
     */
    @Builder.Default
    private String model = "gpt-4o-mini";

    /**
     * The system prompt to use for the extraction.
     */
    @JsonProperty("systemPrompt")
    private String systemPrompt;

    /**
     * Configuration for extraction behavior.
     */
    @JsonProperty("extractConfig")
    private ExtractConfig extractConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractConfig {
        /**
         * Whether to load only the main content of the page.
         */
        @JsonProperty("onlyMainContent")
        @Builder.Default
        private Boolean onlyMainContent = true;

        /**
         * Wait for specific elements before extracting.
         */
        private String waitFor;

        /**
         * Screenshot configuration.
         */
        private Boolean screenshot;

        /**
         * Headers to use for the request.
         */
        private Map<String, String> headers;
    }
}
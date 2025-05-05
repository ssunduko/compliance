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
 * Request model for Firecrawl scrape API v1.
 * Based on the OpenAPI specification for the /scrape endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirecrawlScrapeRequest {

    /**
     * The URL to scrape
     */
    private String url;

    /**
     * Formats to include in the output.
     */
    @Builder.Default
    private List<String> formats = List.of("markdown", "extract");

    /**
     * Tags to include in the output.
     */
    private List<String> includeTags;

    /**
     * Tags to exclude from the output.
     */
    private List<String> excludeTags;

    /**
     * Headers to send with the request. Can be used to send cookies, user-agent, etc.
     */
    private Map<String, String> headers;

    /**
     * Specify a delay in milliseconds before fetching the content, allowing the page sufficient time to load.
     */
    @Builder.Default
    private Integer waitFor = 0;

    /**
     * Timeout in milliseconds for the request
     */
    @Builder.Default
    private Integer timeout = 30000;

    /**
     * Extract object for LLM-based extraction
     */
    private ExtractConfig extract;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractConfig {
        /**
         * The schema to use for the extraction (Optional)
         */
        private JsonNode schema;

        /**
         * The system prompt to use for the extraction (Optional)
         */
        private String systemPrompt;

        /**
         * The prompt to use for the extraction without a schema (Optional)
         */
        private String prompt;
    }
}
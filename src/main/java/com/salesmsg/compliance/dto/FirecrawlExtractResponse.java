package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response model for Firecrawl extract API v1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirecrawlExtractResponse {

    private boolean success;

    /**
     * List of extracted data.
     * Each object corresponds to a URL in the request.
     */
    private List<ExtractedData> data;

    private String warning;

    private String error;

    private String details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedData {

        /**
         * The URL that was extracted from.
         */
        private String url;

        /**
         * The extracted content based on the schema.
         */
        private Map<String, Object> extract;

        /**
         * Metadata about the page.
         */
        private Metadata metadata;

        /**
         * The markdown content of the page.
         */
        private String markdown;

        /**
         * The HTML content of the page.
         */
        private String html;

        /**
         * The raw HTML content of the page.
         */
        @JsonProperty("rawHtml")
        private String rawHtml;

        /**
         * Token usage statistics.
         */
        private TokenUsage llmUsage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {

        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {

        private String title;

        private String description;

        private String language;

        private String keywords;

        private String robots;

        @JsonProperty("ogTitle")
        private String ogTitle;

        @JsonProperty("ogDescription")
        private String ogDescription;

        @JsonProperty("ogUrl")
        private String ogUrl;

        @JsonProperty("ogImage")
        private String ogImage;

        @JsonProperty("ogLocaleAlternate")
        private List<String> ogLocaleAlternate;

        @JsonProperty("ogSiteName")
        private String ogSiteName;

        @JsonProperty("sourceURL")
        private String sourceUrl;

        @JsonProperty("statusCode")
        private Integer statusCode;

        @JsonProperty("error")
        private String error;
    }
}
package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Response model for Firecrawl extract endpoint.
 * Updated to match the actual response structure returned by the Firecrawl API.
 *
 * Initial response example:
 * {
 *   "success": true,
 *   "id": "4ded8a37-63d2-4848-91f8-9c811af3f911",
 *   "urlTrace": []
 * }
 *
 * Status response example:
 * {
 *   "success": true,
 *   "data": [],
 *   "status": "processing",
 *   "expiresAt": "2025-01-08T20:58:12.000Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirecrawlExtractEndpointResponse {

    /**
     * Whether the request was successfully received.
     */
    private boolean success;

    /**
     * The job ID for this extraction request.
     * This is returned in the initial response and used for polling status.
     */
    private String id;

    /**
     * URL trace information (if available).
     */
    private List<String> urlTrace;

    /**
     * Array of extracted data. May be null if processing is not complete.
     */
    private List<ExtractedData> data;

    /**
     * Current status of the extraction process.
     * Possible values: "processing", "completed", "failed"
     */
    private String status;

    /**
     * When the extraction result will expire and no longer be available.
     */
    @JsonProperty("expiresAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private ZonedDateTime expiresAt;

    /**
     * Error message if the request failed.
     */
    private String error;

    /**
     * Additional error details.
     */
    private String details;

    /**
     * Check if this is an initial response with just a job ID.
     *
     * @return true if this is an initial response
     */
    public boolean isInitialResponse() {
        return id != null && (data == null || data.isEmpty()) && status == null;
    }

    /**
     * Check if the extraction is still processing.
     *
     * @return true if the extraction is still processing
     */
    public boolean isProcessing() {
        return "processing".equals(status);
    }

    /**
     * Check if the extraction is completed.
     *
     * @return true if the extraction is completed
     */
    public boolean isCompleted() {
        return "completed".equals(status) && data != null;
    }

    /**
     * Check if the extraction failed.
     *
     * @return true if the extraction failed
     */
    public boolean isFailed() {
        return "failed".equals(status) || (!success && error != null);
    }

    /**
     * Model for extracted data from a URL.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedData {
        /**
         * The URL that was extracted from.
         */
        private String url;

        /**
         * The extracted content from the URL.
         */
        private Object extract;

        /**
         * Metadata about the page.
         */
        private Metadata metadata;

        /**
         * The markdown content of the page if requested.
         */
        private String markdown;

        /**
         * Token usage statistics.
         */
        @JsonProperty("llm_usage")
        private TokenUsage llmUsage;
    }

    /**
     * Model for page metadata.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private String title;
        private String description;
        private String language;
        private String keywords;

        @JsonProperty("sourceURL")
        private String sourceUrl;

        @JsonProperty("statusCode")
        private Integer statusCode;

        private String error;
    }

    /**
     * Model for token usage statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenUsage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
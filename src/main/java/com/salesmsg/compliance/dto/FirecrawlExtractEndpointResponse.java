package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
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
 *   "expiresAt": "2025-05-05T12:54:40.000Z"
 * }
 *
 * Completed response example:
 * {
 *   "success": true,
 *   "data": {...},  // Can be an object or an array
 *   "status": "completed",
 *   "expiresAt": "2025-05-05T13:18:25.000Z"
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
     * Extracted data. May be null if processing is not complete.
     * This can be either a single object or an array depending on the API response.
     * We use JsonNode to handle this polymorphic response.
     */
    private JsonNode data;

    /**
     * Current status of the extraction process.
     * Possible values: "processing", "completed", "failed"
     */
    private String status;

    /**
     * When the extraction result will expire and no longer be available.
     */
    @JsonProperty("expiresAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
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
     * Get the extracted data as a list of ExtractedData objects.
     * This method handles both array and object responses.
     *
     * @return A list of extracted data objects
     */
    public List<ExtractedData> getExtractedDataList() {
        List<ExtractedData> result = new ArrayList<>();

        if (data == null) {
            return result;
        }

        if (data.isArray()) {
            // Handle array response
            for (JsonNode item : data) {
                // Convert each item to ExtractedData using Jackson
                result.add(convertJsonNodeToExtractedData(item));
            }
        } else if (data.isObject()) {
            // Handle single object response by creating a single ExtractedData object
            ExtractedData extractedData = new ExtractedData();
            extractedData.setExtract(data);
            result.add(extractedData);
        }

        return result;
    }

    /**
     * Helper method to convert a JsonNode to an ExtractedData object.
     * In a real implementation, you would use ObjectMapper for this.
     */
    private ExtractedData convertJsonNodeToExtractedData(JsonNode node) {
        // This is a simplified implementation
        // In real code, you would use ObjectMapper.treeToValue()
        ExtractedData data = new ExtractedData();
        data.setExtract(node);
        return data;
    }

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
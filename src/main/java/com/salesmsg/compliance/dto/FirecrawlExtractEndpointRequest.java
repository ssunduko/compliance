package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for Firecrawl extract endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirecrawlExtractEndpointRequest {

    /**
     * The URLs to extract data from.
     */
    private List<String> urls;

    /**
     * Prompt to guide the extraction process.
     */
    private String prompt;

    /**
     * Schema to define the structure of the extracted data.
     * Using JsonNode to ensure proper JSON serialization.
     */
    @JsonSerialize
    private JsonNode schema;
}
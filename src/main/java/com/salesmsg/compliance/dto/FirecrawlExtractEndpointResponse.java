package com.salesmsg.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response model for Firecrawl extract endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirecrawlExtractEndpointResponse {

    private boolean success;

    private Map<String, Object> data;
}
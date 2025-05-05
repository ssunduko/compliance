package com.salesmsg.compliance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.FirecrawlExtractEndpointRequest;
import com.salesmsg.compliance.dto.FirecrawlExtractEndpointResponse;
import com.salesmsg.compliance.dto.FirecrawlScrapeRequest;
import com.salesmsg.compliance.dto.FirecrawlScrapeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with the Firecrawl API.
 * Updated to handle ID-based asynchronous processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirecrawlApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${firecrawl.api.key}")
    private String apiKey;

    @Value("${firecrawl.api.base-url:https://api.firecrawl.dev/v1}")
    private String baseUrl;

    @Value("${firecrawl.api.poll-interval:2000}")
    private long pollIntervalMillis;

    @Value("${firecrawl.api.max-polls:15}")
    private int maxPolls;

    /**
     * Scrape a single URL and optionally extract information using an LLM
     *
     * @param request The scrape request
     * @return The scrape response
     */
    public FirecrawlScrapeResponse scrape(FirecrawlScrapeRequest request) {
        String url = baseUrl + "/scrape";

        HttpHeaders headers = createHeaders();
        HttpEntity<FirecrawlScrapeRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.debug("Calling Firecrawl API at: {}", url);
            log.debug("Request payload: {}", request);

            ResponseEntity<FirecrawlScrapeResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FirecrawlScrapeResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                FirecrawlScrapeResponse responseBody = response.getBody();
                if (responseBody != null && !responseBody.isSuccess()) {
                    log.error("Firecrawl API returned success=false");
                }
                return responseBody;
            } else {
                log.error("Firecrawl API error. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Firecrawl API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling Firecrawl API: {}", e.getMessage());
            log.debug("Full error details:", e);
            throw new RuntimeException("Failed to call Firecrawl API: " + e.getMessage(), e);
        }
    }

    /**
     * Extract structured data from multiple URLs using the /extract endpoint.
     * This method handles the asynchronous nature of the API by polling for results.
     *
     * @param request The extract request
     * @return The extract response with completed results
     */
    public FirecrawlExtractEndpointResponse extractFromUrls(FirecrawlExtractEndpointRequest request) {
        String url = baseUrl + "/extract";

        HttpHeaders headers = createHeaders();
        HttpEntity<FirecrawlExtractEndpointRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.debug("Calling Firecrawl Extract API at: {}", url);

            // Log request details
            try {
                String jsonRequest = objectMapper.writeValueAsString(request);
                log.debug("JSON Request: {}", jsonRequest);
            } catch (Exception e) {
                log.warn("Failed to serialize request for logging", e);
            }

            // Make the initial API call
            ResponseEntity<String> rawResponse = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Log full raw response for debugging
            log.debug("Initial Raw API Response: {}", rawResponse.getBody());

            // Process response
            if (rawResponse.getStatusCode().is2xxSuccessful()) {
                try {
                    // Parse the response
                    FirecrawlExtractEndpointResponse responseBody = objectMapper.readValue(
                            rawResponse.getBody(),
                            FirecrawlExtractEndpointResponse.class
                    );

                    if (!responseBody.isSuccess()) {
                        log.error("Firecrawl Extract API returned success=false: {}",
                                responseBody.getError() != null ? responseBody.getError() : "No error details");
                        return responseBody;
                    }

                    // Check if we need to poll for results
                    if (responseBody.isInitialResponse() && responseBody.getId() != null) {
                        log.info("Received job ID: {}. Polling for results...", responseBody.getId());
                        return pollForResults(responseBody.getId());
                    } else if (responseBody.isProcessing()) {
                        log.info("Extraction in progress. Polling for results...");
                        return pollForResults(responseBody.getId());
                    }

                    if (responseBody.getData() == null) {
                        log.warn("No data in response and no job ID to poll. Returning as is.");
                    } else {
                        log.debug("Extraction completed immediately");
                    }

                    return responseBody;
                } catch (Exception e) {
                    log.error("Error parsing Firecrawl Extract API response: {}", e.getMessage());
                    log.debug("Response body that failed to parse: {}", rawResponse.getBody());
                    throw new RuntimeException("Failed to parse Firecrawl Extract API response: " + e.getMessage(), e);
                }
            } else {
                log.error("Firecrawl Extract API error. Status: {}, Body: {}",
                        rawResponse.getStatusCode(), rawResponse.getBody());
                throw new RuntimeException("Firecrawl Extract API error: " + rawResponse.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling Firecrawl Extract API: {}", e.getMessage());
            log.debug("Full error details:", e);
            throw new RuntimeException("Failed to call Firecrawl Extract API: " + e.getMessage(), e);
        }
    }

    /**
     * Poll for results of an asynchronous extraction.
     *
     * @param jobId The job ID to poll for
     * @return The final response with extraction results
     */
    private FirecrawlExtractEndpointResponse pollForResults(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            log.warn("No job ID provided for polling");
            return createEmptyResponse("No job ID provided for polling");
        }

        // Implement polling with exponential backoff
        int pollCount = 0;
        long currentInterval = pollIntervalMillis;

        String pollUrl = baseUrl + "/extract/" + jobId;
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        while (pollCount < maxPolls) {
            try {
                // Wait before polling
                TimeUnit.MILLISECONDS.sleep(currentInterval);

                // Make poll request
                log.debug("Polling for extraction results: {}/{} (job ID: {})",
                        pollCount + 1, maxPolls, jobId);

                ResponseEntity<String> pollResponse = restTemplate.exchange(
                        pollUrl,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                if (pollResponse.getStatusCode().is2xxSuccessful()) {
                    log.debug("Poll response: {}", pollResponse.getBody());

                    // Parse response
                    FirecrawlExtractEndpointResponse response = objectMapper.readValue(
                            pollResponse.getBody(),
                            FirecrawlExtractEndpointResponse.class
                    );

                    // Check if complete or failed
                    if (response.isCompleted()) {
                        log.info("Extraction completed after {} polls", pollCount + 1);
                        return response;
                    } else if (response.isFailed()) {
                        log.warn("Extraction failed: {}", response.getError());
                        return response;
                    }
                } else {
                    log.warn("Poll request failed: {}", pollResponse.getStatusCode());
                    return createEmptyResponse("Poll request failed: " + pollResponse.getStatusCode());
                }

                // Increment counter and backoff interval
                pollCount++;
                currentInterval = Math.min(currentInterval * 2, 30000); // Max 30 seconds
            } catch (Exception e) {
                log.error("Error polling for results: {}", e.getMessage());
                return createEmptyResponse("Error polling for results: " + e.getMessage());
            }
        }

        // If we get here, we reached max polls
        log.warn("Reached maximum poll count ({}) but extraction is still processing", maxPolls);
        return createEmptyResponse("Extraction timed out after " + maxPolls + " poll attempts");
    }

    /**
     * Create an empty response with an error message.
     *
     * @param errorMessage The error message
     * @return An empty response
     */
    private FirecrawlExtractEndpointResponse createEmptyResponse(String errorMessage) {
        return FirecrawlExtractEndpointResponse.builder()
                .success(false)
                .error(errorMessage)
                .build();
    }

    /**
     * Create HTTP headers for Firecrawl API requests.
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }
}
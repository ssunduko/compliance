package com.salesmsg.compliance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.FirecrawlExtractEndpointResponse;
import com.salesmsg.compliance.dto.FirecrawlExtractEndpointRequest;
import com.salesmsg.compliance.dto.FirecrawlScrapeRequest;
import com.salesmsg.compliance.dto.FirecrawlScrapeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client for interacting with the Firecrawl API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirecrawlApiClient {

    private final RestTemplate restTemplate;

    @Value("${firecrawl.api.key}")
    private String apiKey;

    @Value("${firecrawl.api.base-url:https://api.firecrawl.dev/v1}")
    private String baseUrl;

    /**
     * Scrape a single URL and optionally extract information using an LLM
     *
     * @param request The scrape request
     * @return The scrape response
     */
    public FirecrawlScrapeResponse scrape(FirecrawlScrapeRequest request) {
        String url = baseUrl + "/scrape";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

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
     * Extract structured data from multiple URLs using the /extract endpoint
     *
     * @param request The extract request
     * @return The extract response
     */
    public FirecrawlExtractEndpointResponse extractFromUrls(FirecrawlExtractEndpointRequest request) {
        String url = baseUrl + "/extract";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<FirecrawlExtractEndpointRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.debug("Calling Firecrawl Extract API at: {}", url);
            log.debug("Request payload: {}", request);

            // Try to serialize the request to see what's being sent
            if (log.isDebugEnabled()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonRequest = mapper.writeValueAsString(request);
                    log.debug("JSON Request: {}", jsonRequest);

                    System.out.println("This is my request: " + jsonRequest);

                } catch (Exception e) {
                    log.warn("Failed to serialize request for logging", e);
                }
            }

            ResponseEntity<FirecrawlExtractEndpointResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FirecrawlExtractEndpointResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                FirecrawlExtractEndpointResponse responseBody = response.getBody();
                if (responseBody != null && !responseBody.isSuccess()) {
                    log.error("Firecrawl Extract API returned success=false");
                }
                System.out.println("This is my response: " + responseBody);

                return responseBody;
            } else {
                log.error("Firecrawl Extract API error. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Firecrawl Extract API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling Firecrawl Extract API: {}", e.getMessage());
            log.debug("Full error details:", e);
            throw new RuntimeException("Failed to call Firecrawl Extract API: " + e.getMessage(), e);
        }
    }
}
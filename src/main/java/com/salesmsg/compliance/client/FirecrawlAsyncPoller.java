package com.salesmsg.compliance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.FirecrawlExtractEndpointResponse;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Utility class for polling asynchronous Firecrawl API operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirecrawlAsyncPoller {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Poll for the completion of an asynchronous operation.
     *
     * @param config The polling configuration
     * @param <T> The type of response expected
     * @return The completed response
     */
    public <T> T pollUntilComplete(PollConfig<T> config) {
        T currentResponse = config.getInitialResponse();
        int pollCount = 0;
        long currentInterval = config.getInitialIntervalMillis();

        while (!config.getCompletionCheck().test(currentResponse) && pollCount < config.getMaxPolls()) {
            try {
                // Wait before polling
                TimeUnit.MILLISECONDS.sleep(currentInterval);

                // Make poll request
                log.debug("Polling for results: {}/{}", pollCount + 1, config.getMaxPolls());

                HttpEntity<?> entity = new HttpEntity<>(config.getHeaders());
                ResponseEntity<String> pollResponse = restTemplate.exchange(
                        config.getPollUrl(),
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                if (pollResponse.getStatusCode().is2xxSuccessful()) {
                    // Parse response
                    try {
                        currentResponse = objectMapper.readValue(
                                pollResponse.getBody(),
                                config.getResponseClass()
                        );

                        // Check for errors
                        if (config.getErrorCheck().test(currentResponse)) {
                            log.warn("Error detected in poll response");
                            return currentResponse;
                        }

                        // Check if complete
                        if (config.getCompletionCheck().test(currentResponse)) {
                            log.info("Operation completed after {} polls", pollCount + 1);
                            return currentResponse;
                        }
                    } catch (Exception e) {
                        log.error("Error parsing poll response: {}", e.getMessage());
                        break;
                    }
                } else {
                    log.warn("Poll request failed: {}", pollResponse.getStatusCode());
                    break;
                }

                // Increment counter and backoff interval (exponential backoff)
                pollCount++;
                currentInterval = (long) Math.min(currentInterval * config.getBackoffMultiplier(), config.getMaxIntervalMillis());

                // Allow the caller to process intermediate results
                if (config.getIntermediateResultHandler() != null) {
                    config.getIntermediateResultHandler().accept(currentResponse, pollCount);
                }
            } catch (Exception e) {
                log.error("Error during polling: {}", e.getMessage());
                break;
            }
        }

        // Log if we maxed out polls
        if (!config.getCompletionCheck().test(currentResponse) && pollCount >= config.getMaxPolls()) {
            log.warn("Reached maximum poll count ({}) but operation is still processing", config.getMaxPolls());
        }

        return currentResponse;
    }

    /**
     * Start polling in a background thread.
     *
     * @param config The polling configuration
     * @param <T> The type of response expected
     * @return A CompletableFuture that will complete with the final response
     */
    public <T> CompletableFuture<T> pollAsync(PollConfig<T> config) {
        return CompletableFuture.supplyAsync(() -> pollUntilComplete(config));
    }

    /**
     * Configuration for polling operations.
     *
     * @param <T> The type of response expected
     */
    @Data
    @Builder
    public static class PollConfig<T> {
        /**
         * The initial response from the API that started the async operation.
         */
        private T initialResponse;

        /**
         * The URL to poll for status updates.
         */
        private String pollUrl;

        /**
         * HTTP headers to include in poll requests.
         */
        private HttpHeaders headers;

        /**
         * The class of the response type.
         */
        private Class<T> responseClass;

        /**
         * Predicate that returns true when the operation is complete.
         */
        private Predicate<T> completionCheck;

        /**
         * Predicate that returns true if an error is detected.
         */
        private Predicate<T> errorCheck;

        /**
         * Initial polling interval in milliseconds.
         */
        @Builder.Default
        private long initialIntervalMillis = 1000;

        /**
         * Maximum polling interval in milliseconds.
         */
        @Builder.Default
        private long maxIntervalMillis = 30000;

        /**
         * Multiplier for exponential backoff.
         */
        @Builder.Default
        private double backoffMultiplier = 1.5;

        /**
         * Maximum number of poll requests.
         */
        @Builder.Default
        private int maxPolls = 15;

        /**
         * Optional handler for intermediate results during polling.
         */
        private IntermediateResultHandler<T> intermediateResultHandler;
    }

    /**
     * Functional interface for handling intermediate results during polling.
     *
     * @param <T> The type of response
     */
    @FunctionalInterface
    public interface IntermediateResultHandler<T> {
        void accept(T response, int pollCount);
    }

    /**
     * Create a polling configuration specifically for Firecrawl extraction operations.
     *
     * @param initialResponse The initial response from the extraction request
     * @param requestId The request ID for polling
     * @param baseUrl The base URL of the Firecrawl API
     * @param apiKey The API key for authentication
     * @return A polling configuration
     */
    public PollConfig<FirecrawlExtractEndpointResponse> createExtractionPollConfig(
            FirecrawlExtractEndpointResponse initialResponse,
            String requestId,
            String baseUrl,
            String apiKey) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);

        return PollConfig.<FirecrawlExtractEndpointResponse>builder()
                .initialResponse(initialResponse)
                .pollUrl(baseUrl + "/status/" + requestId)
                .headers(headers)
                .responseClass(FirecrawlExtractEndpointResponse.class)
                .completionCheck(response -> "completed".equals(response.getStatus()))
                .errorCheck(response -> "failed".equals(response.getStatus()))
                .initialIntervalMillis(2000)
                .maxPolls(20)
                .build();
    }
}

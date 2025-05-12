package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.WebsiteCheckDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for validating websites for 10DLC compliance.
 * Checks websites for required elements like privacy policies, terms of service,
 * and proper opt-in mechanisms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebsiteValidationService {

    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final ObjectMapper objectMapper;

    private static final String WEBSITE_VALIDATION_PROMPT = """
            You are a Website Compliance Expert for 10DLC SMS campaigns. Your task is to evaluate
            if the provided website meets carrier requirements for SMS messaging.
            
            Website URL: %s
            Webform URL (if applicable): %s
            
            Analyze the website for the following compliance elements:
            1. Privacy policy that includes SMS data usage
            2. Terms and conditions mentioning SMS communications
            3. Clear opt-in mechanism (webform, checkbox, etc.)
            4. Clear disclosure of message frequency
            5. Explicit mention of STOP opt-out instructions
            6. Business identity clearly visible
            7. Message and data rates disclosure
            
            Provide your analysis in JSON format with the following structure:
            {
              "has_privacy_policy": boolean,
              "privacy_policy_url": string (if found),
              "has_sms_data_sharing_clause": boolean,
              "webform_functional": boolean,
              "webform_has_required_elements": boolean,
              "compliance_score": float (0-100),
              "issues": [
                {
                  "severity": "critical" | "major" | "minor",
                  "description": string,
                  "recommendation": string
                }
              ]
            }
            """;

    /**
     * Check a website for SMS compliance requirements.
     *
     * @param request The website check request
     * @return The website check results
     */
    public WebsiteCheckDTO checkWebsite(WebsiteCheckDTO request) {
        log.info("Checking website compliance for URL: {}", request.getUrl());

        try {
            // First, fetch the website content
            String websiteContent = fetchWebsiteContent(request.getUrl());

            // Format the system prompt with request parameters
            String systemPrompt = String.format(
                    WEBSITE_VALIDATION_PROMPT,
                    request.getUrl(),
                    request.getWebformUrl() != null ? request.getWebformUrl() : "Not provided"
            );

            // Create the user message with website content
            StringBuilder userMessageContent = new StringBuilder("Please analyze this website for 10DLC compliance:\n\n");
            userMessageContent.append("Website content (excerpt):\n");
            userMessageContent.append(websiteContent);


            // Fetch webform content if applicable
            if (request.getCheckWebform() && request.getWebformUrl() != null) {
                String webformContent = fetchWebsiteContent(request.getWebformUrl());
                userMessageContent.append("Webform content (excerpt):\n");
                userMessageContent.append(webformContent);
            }

            // Create the prompt
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(systemPrompt));
            promptMessages.add(new UserMessage(userMessageContent.toString()));
            Prompt prompt = new Prompt(promptMessages);

            // Execute the chat completion
            ChatResponse response = chatClient
                    .prompt(prompt)
                    //.advisors(retrievalAugmentationAdvisor)
                    .call()
                    .chatResponse();

            String responseText = response.getResult().getOutput().getText();

            // Parse the JSON response
            Map<String, Object> analysisResult = parseJsonResponse(responseText);

            // Build and return the DTO
            return buildWebsiteCheckDTO(analysisResult, request);

        } catch (Exception e) {
            log.error("Error checking website compliance", e);
            return buildErrorResponse("Failed to check website: " + e.getMessage());
        }
    }

    /**
     * Fetch website content for analysis.
     *
     * @param url The website URL
     * @return The website content
     */
    private String fetchWebsiteContent(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 Compliance Checker")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                log.warn("Failed to fetch website content, status: {}", response.statusCode());
                return "Failed to fetch content, status: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("Error fetching website content", e);
            return "Error fetching content: " + e.getMessage();
        }
    }

    /**
     * Build a WebsiteCheckDTO from the analysis result.
     *
     * @param analysisResult The raw analysis result
     * @param request The original request
     * @return The formatted DTO
     */
    private WebsiteCheckDTO buildWebsiteCheckDTO(Map<String, Object> analysisResult, WebsiteCheckDTO request) {
        // Extract values from the analysis result
        Boolean hasPrivacyPolicy = (Boolean) analysisResult.getOrDefault("has_privacy_policy", false);
        String privacyPolicyUrl = (String) analysisResult.get("privacy_policy_url");
        Boolean hasSmsDataSharingClause = (Boolean) analysisResult.getOrDefault("has_sms_data_sharing_clause", false);
        Boolean webformFunctional = (Boolean) analysisResult.getOrDefault("webform_functional", false);
        Boolean webformHasRequiredElements = (Boolean) analysisResult.getOrDefault("webform_has_required_elements", false);
        Float complianceScore = ((Number) analysisResult.getOrDefault("compliance_score", 0)).floatValue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) analysisResult.getOrDefault("issues", new ArrayList<>());

        // Map issues to DTOs
        List<WebsiteCheckDTO.WebsiteIssueDTO> issueDTOs = issues.stream()
                .map(issue -> WebsiteCheckDTO.WebsiteIssueDTO.builder()
                        .severity((String) issue.get("severity"))
                        .description((String) issue.get("description"))
                        .recommendation((String) issue.get("recommendation"))
                        .build())
                .collect(Collectors.toList());

        // Build the DTO
        return WebsiteCheckDTO.builder()
                .url(request.getUrl())
                .webformUrl(request.getWebformUrl())
                .checkWebform(request.getCheckWebform())
                .hasPrivacyPolicy(hasPrivacyPolicy)
                .privacyPolicyUrl(privacyPolicyUrl)
                .hasSmsDataSharingClause(hasSmsDataSharingClause)
                .webformFunctional(webformFunctional)
                .webformHasRequiredElements(webformHasRequiredElements)
                .complianceScore(complianceScore)
                .issues(issueDTOs)
                .build();
    }

    /**
     * Build an error response DTO.
     *
     * @param errorMessage The error message
     * @return An error response DTO
     */
    private WebsiteCheckDTO buildErrorResponse(String errorMessage) {
        WebsiteCheckDTO.WebsiteIssueDTO error = WebsiteCheckDTO.WebsiteIssueDTO.builder()
                .severity("critical")
                .description("Error during website check: " + errorMessage)
                .recommendation("Please verify the website URL and try again")
                .build();

        return WebsiteCheckDTO.builder()
                .complianceScore(0f)
                .issues(Collections.singletonList(error))
                .build();
    }

    /**
     * Parse a JSON response from the AI model.
     *
     * @param responseText The response text
     * @return The parsed JSON as a Map
     */
    private Map<String, Object> parseJsonResponse(String responseText) {
        try {
            // Extract JSON from response (in case there's additional text around it)
            String jsonContent = extractJsonFromResponse(responseText);

            // Parse the JSON
            return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});

        } catch (JsonProcessingException e) {
            log.error("Error parsing AI response", e);
            return Map.of();
        }
    }

    /**
     * Extract JSON content from a text response that might contain additional text.
     *
     * @param response The response text that should contain a JSON object
     * @return The extracted JSON string
     */
    private String extractJsonFromResponse(String response) {
        // Look for JSON pattern using regex
        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group();
        }

        // If no JSON found, return the original response
        return response;
    }
}
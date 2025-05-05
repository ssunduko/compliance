package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.*;
import com.salesmsg.compliance.client.FirecrawlApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static software.amazon.awssdk.regions.internal.util.EC2MetadataUtils.getData;

/**
 * Alternative implementation of WebsiteValidationService using Firecrawl's extract endpoint.
 * Extracts specific compliance-related content from websites and analyzes it using AI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirecrawlWebsiteValidationService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final FirecrawlApiClient firecrawlApiClient;

    // Define the schema for extracting compliance-related content from websites
    private static final String COMPLIANCE_EXTRACTION_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "privacy_policy": {
              "type": "object",
              "properties": {
                "exists": { "type": "boolean" },
                "url": { "type": "string" },
                "sms_data_clauses": { 
                  "type": "array",
                  "items": { "type": "string" }
                },
                "data_sharing_clauses": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "opt_out_instructions": { "type": "string" },
                "contact_information": { "type": "string" }
              }
            },
            "terms_of_service": {
              "type": "object", 
              "properties": {
                "exists": { "type": "boolean" },
                "url": { "type": "string" },
                "sms_services_section": { "type": "string" },
                "message_frequency": { "type": "string" },
                "rates_disclosure": { "type": "string" },
                "stop_instructions": { "type": "string" }
              }
            },
            "opt_in_forms": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "form_type": { "type": "string" },
                  "has_sms_checkbox": { "type": "boolean" },
                  "consent_text": { "type": "string" },
                  "frequency_disclosure": { "type": "boolean" },
                  "opt_out_instructions": { "type": "boolean" },
                  "terms_link": { "type": "boolean" },
                  "privacy_link": { "type": "boolean" }
                }
              }
            },
            "business_identity": {
              "type": "object",
              "properties": {
                "clearly_visible": { "type": "boolean" },
                "business_name": { "type": "string" },
                "contact_information": { "type": "string" }
              }
            }
          }
        }
        """;

    private static final String EXTRACTION_PROMPT = """
        Extract all compliance-related information from this website, focusing on:
        1. Privacy policy - especially SMS data usage clauses
        2. Terms of service - SMS communications information
        3. Opt-in forms - SMS consent checkboxes and text
        4. Business identity - company name and contact information
        
        Follow the schema provided to structure the extracted data.
        """;

    private static final String WEBSITE_ANALYSIS_PROMPT = """
            You are a Website Compliance Expert for 10DLC SMS campaigns. Based on the extracted website content,
            evaluate if the website meets carrier requirements for SMS messaging.
            
            Website URL: %s
            Extracted Content:
            %s
            
            Analyze the extracted information for the following compliance requirements:
            1. Privacy policy with SMS data usage clauses
            2. Terms and conditions mentioning SMS communications
            3. Clear opt-in mechanisms for SMS messaging
            4. Message frequency disclosure
            5. STOP opt-out instructions
            6. Business identity clearly visible
            7. Message and data rates disclosure
            
            Provide your analysis in JSON format:
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
     * Check a website for SMS compliance requirements using Firecrawl extraction.
     *
     * @param request The website check request
     * @return The website check results
     */
    public WebsiteCheckDTO checkWebsite(WebsiteCheckDTO request) {
        log.info("Checking website compliance using Firecrawl for URL: {}", request.getUrl());

        try {
            // Build list of URLs to extract from
            List<String> urls = new ArrayList<>();
            urls.add(request.getUrl());

            if (Boolean.TRUE.equals(request.getCheckWebform()) && request.getWebformUrl() != null) {
                urls.add(request.getWebformUrl());
            }

            // Extract compliance-related content from all URLs using the extract endpoint
            FirecrawlExtractEndpointResponse extractedContent = extractContentFromWebsites(urls);

            // Analyze the extracted content using AI
            Map<String, Object> analysisResult = analyzeExtractedContent(
                    request.getUrl(),
                    extractedContent
            );

            // Build and return the DTO
            return buildWebsiteCheckDTO(analysisResult, request);

        } catch (Exception e) {
            log.error("Error checking website compliance", e);
            return buildErrorResponse("Failed to check website: " + e.getMessage());
        }
    }

    /**
     * Extract compliance-related content from websites using Firecrawl extract endpoint.
     *
     * @param urls The website URLs to extract from
     * @return The extracted content
     */
    private FirecrawlExtractEndpointResponse extractContentFromWebsites(List<String> urls) {
        try {
            // Parse the schema as JsonNode
            JsonNode schema = objectMapper.readTree(COMPLIANCE_EXTRACTION_SCHEMA);

            // Prepare the Firecrawl extract request
            //TODO: Refactor
            /*FirecrawlExtractEndpointRequest extractRequest = FirecrawlExtractEndpointRequest.builder()
                    .urls(urls)
                    .prompt(EXTRACTION_PROMPT)
                    .schema(schema)
                    .build();*/

            FirecrawlExtractEndpointRequest extractRequest = FirecrawlExtractEndpointRequest.builder()
                    .urls(urls)
                    .prompt("Extract the company mission and features from these pages.")
                    //.schema(schema)
                    .build();

            FirecrawlExtractEndpointResponse response = firecrawlApiClient.extractFromUrls(extractRequest);

            Map<String, Object> map = response.getData();
            map.forEach((k, v) -> System.out.printf("%-15s : %s%n", k, v));

            // Make the API call to Firecrawl
            return firecrawlApiClient.extractFromUrls(extractRequest);

        } catch (Exception e) {
            log.error("Error extracting content from websites", e);
            throw new RuntimeException("Failed to extract content from websites", e);
        }
    }

    /**
     * Analyze the extracted content using AI to check for compliance.
     *
     * @param url The main website URL
     * @param extractedContent The extracted content response
     * @return The analysis result
     */
    private Map<String, Object> analyzeExtractedContent(
            String url,
            FirecrawlExtractEndpointResponse extractedContent) {

        try {
            // Get the extracted data
            Map<String, Object> combinedContent = new HashMap<>();

            if (extractedContent != null && extractedContent.isSuccess() &&
                    extractedContent.getData() != null) {
                combinedContent = extractedContent.getData();
            }

            // Format the system prompt with extracted content
            String systemPrompt = String.format(
                    WEBSITE_ANALYSIS_PROMPT,
                    url,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(combinedContent)
            );

            // Create the user message
            String userPrompt = "Please analyze the extracted website content for 10DLC compliance.";

            // Create the prompt
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(systemPrompt));
            promptMessages.add(new UserMessage(userPrompt));
            Prompt prompt = new Prompt(promptMessages);

            // Execute the chat completion
            ChatResponse response = chatClient
                    .prompt(prompt)
                    .call()
                    .chatResponse();

            String responseText = response.getResult().getOutput().getText();

            // Parse the JSON response
            return parseJsonResponse(responseText);

        } catch (Exception e) {
            log.error("Error analyzing extracted content", e);
            throw new RuntimeException("Failed to analyze extracted content", e);
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
            // Extract JSON from response
            String jsonContent = extractJsonFromResponse(responseText);

            // Parse the JSON
            return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});

        } catch (JsonProcessingException e) {
            log.error("Error parsing AI response", e);
            return Map.of(
                    "has_privacy_policy", false,
                    "has_sms_data_sharing_clause", false,
                    "webform_functional", false,
                    "webform_has_required_elements", false,
                    "compliance_score", 0,
                    "issues", List.of(Map.of(
                            "severity", "critical",
                            "description", "Error parsing AI response: " + e.getMessage(),
                            "recommendation", "Please try again or contact support"
                    ))
            );
        }
    }

    /**
     * Extract JSON content from a text response that might contain additional text.
     *
     * @param response The response text that should contain a JSON object
     * @return The extracted JSON string
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }

        // Find the first opening brace
        int startBrace = response.indexOf('{');
        if (startBrace == -1) {
            return "{}";
        }

        // Track opening and closing braces
        int braceCount = 1;
        int endBrace = -1;

        for (int i = startBrace + 1; i < response.length(); i++) {
            char c = response.charAt(i);

            // Skip escaped characters
            if (c == '\\' && i + 1 < response.length()) {
                i++;
                continue;
            }

            // Skip string literals
            if (c == '"') {
                i++;
                while (i < response.length()) {
                    char stringChar = response.charAt(i);
                    if (stringChar == '\\' && i + 1 < response.length()) {
                        i += 2;
                        continue;
                    }
                    if (stringChar == '"') {
                        break;
                    }
                    i++;
                }
                continue;
            }

            // Count braces
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    endBrace = i;
                    break;
                }
            }
        }

        if (endBrace != -1) {
            return response.substring(startBrace, endBrace + 1);
        }

        return "{}";
    }
}

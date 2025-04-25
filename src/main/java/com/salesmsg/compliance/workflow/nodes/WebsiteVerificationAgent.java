package com.salesmsg.compliance.workflow.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.workflow.VerificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LangGraph node for verifying website compliance for a 10DLC submission.
 * Analyzes the website for privacy policy, terms, and opt-in mechanisms.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebsiteVerificationAgent {

    private final ComplianceSubmissionRepository submissionRepository;
    private final VerificationOrchestrator verificationOrchestrator;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String WEBSITE_VALIDATION_PROMPT = """
            You are a Website Compliance Expert for 10DLC SMS campaigns. Your task is to evaluate
            if the provided website meets carrier requirements for SMS messaging.
            
            Website URL: %s
            Business Type: %s
            Use Case: %s
            
            Analyze the website for the following compliance elements:
            1. Privacy policy that includes SMS data usage
            2. Terms and conditions mentioning SMS communications
            3. Clear opt-in mechanism (webform, checkbox, etc.)
            4. Clear disclosure of message frequency
            5. Explicit mention of STOP opt-out instructions
            6. Business identity clearly visible
            7. Message and data rates disclosure
            
            Website content excerpt:
            %s
            
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
     * Process the website for compliance verification.
     *
     * @param state The current verification state
     * @return Updated state with website verification results
     */
    public Map<String, Object> process(Map<String, Object> state) {
        String verificationId = (String) state.get("verification_id");
        String submissionId = (String) state.get("submission_id");

        log.info("Processing website verification for submission: {}", submissionId);

        try {
            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 70, "website");

            // Get the submission
            ComplianceSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

            // Get website URL from the submission
            String websiteUrl = submission.getWebsiteUrl();

            if (websiteUrl == null || websiteUrl.isEmpty()) {
                log.warn("No website URL provided for submission: {}", submissionId);

                // Create a placeholder result with neutral score
                Map<String, Object> newState = new HashMap<>(state);

                @SuppressWarnings("unchecked")
                Map<String, Object> componentScores = (Map<String, Object>)
                        newState.computeIfAbsent("component_scores", k -> new HashMap<>());

                // No website means N/A for this component - using null instead of a low score
                componentScores.put("website", null);

                // Add a recommendation about website
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> recommendations = (List<Map<String, Object>>)
                        newState.computeIfAbsent("recommendations", k -> new ArrayList<>());

                Map<String, Object> recommendation = new HashMap<>();
                recommendation.put("component", "website");
                recommendation.put("priority", "medium");
                recommendation.put("description", "No website URL provided");
                recommendation.put("action", "Adding a website with clear SMS opt-in processes improves campaign compliance");
                recommendations.add(recommendation);

                // Update verification progress
                verificationOrchestrator.updateVerificationProgress(verificationId, 80, "website");

                return newState;
            }

            // Fetch website content
            String websiteContent = fetchWebsiteContent(websiteUrl);

            // Format the system prompt with website details
            String systemPrompt = String.format(
                    WEBSITE_VALIDATION_PROMPT,
                    websiteUrl,
                    submission.getBusinessType(),
                    submission.getUseCase(),
                    websiteContent.substring(0, Math.min(websiteContent.length(), 2000)) + "...[content truncated]..."
            );

            // Create the user message
            String userPrompt = "Please analyze this website for 10DLC compliance.";

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
            Map<String, Object> analysisResult = parseJsonResponse(responseText);

            // Extract results
            Boolean hasPrivacyPolicy = (Boolean) analysisResult.getOrDefault("has_privacy_policy", false);
            String privacyPolicyUrl = (String) analysisResult.get("privacy_policy_url");
            Boolean hasSmsDataSharingClause = (Boolean) analysisResult.getOrDefault("has_sms_data_sharing_clause", false);
            Boolean webformFunctional = (Boolean) analysisResult.getOrDefault("webform_functional", false);
            Boolean webformHasRequiredElements = (Boolean) analysisResult.getOrDefault("webform_has_required_elements", false);
            Float complianceScore = ((Number) analysisResult.getOrDefault("compliance_score", 0)).floatValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issues = (List<Map<String, Object>>) analysisResult.getOrDefault("issues", new ArrayList<>());

            // Prepare state updates
            List<Map<String, Object>> criticalIssuesList = new ArrayList<>();
            List<Map<String, Object>> recommendationsList = new ArrayList<>();

            // Process issues and build recommendations
            for (Map<String, Object> issue : issues) {
                // Process critical issues
                if ("critical".equals(issue.get("severity"))) {
                    Map<String, Object> criticalIssue = new HashMap<>();
                    criticalIssue.put("component", "website");
                    criticalIssue.put("description", issue.get("description"));
                    criticalIssue.put("recommendation", issue.get("recommendation"));
                    criticalIssuesList.add(criticalIssue);
                }

                // Add all issues as recommendations
                Map<String, Object> recommendation = new HashMap<>();
                recommendation.put("component", "website");
                recommendation.put("priority", mapSeverityToPriority((String)issue.get("severity")));
                recommendation.put("description", issue.get("description"));
                recommendation.put("action", issue.get("recommendation"));
                recommendationsList.add(recommendation);
            }

            // Update state with results
            Map<String, Object> newState = new HashMap<>(state);

            // Update component scores
            @SuppressWarnings("unchecked")
            Map<String, Object> componentScores = (Map<String, Object>)
                    newState.computeIfAbsent("component_scores", k -> new HashMap<>());
            componentScores.put("website", complianceScore);

            // Add critical issues to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> criticalIssues = (List<Map<String, Object>>)
                    newState.computeIfAbsent("critical_issues", k -> new ArrayList<>());
            criticalIssues.addAll(criticalIssuesList);

            // Add recommendations to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendations = (List<Map<String, Object>>)
                    newState.computeIfAbsent("recommendations", k -> new ArrayList<>());
            recommendations.addAll(recommendationsList);

            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 80, "website");

            return newState;

        } catch (Exception e) {
            log.error("Error during website verification for submission: {}", submissionId, e);
            throw new RuntimeException("Website verification failed", e);
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
            return Map.of(
                    "has_privacy_policy", false,
                    "privacy_policy_url", "",
                    "has_sms_data_sharing_clause", false,
                    "webform_functional", false,
                    "webform_has_required_elements", false,
                    "compliance_score", 0.0f,
                    "issues", List.of(Map.of(
                            "severity", "critical",
                            "description", "Error analyzing website: " + e.getMessage(),
                            "recommendation", "Please verify the website URL and try again"
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
        // Look for JSON pattern using regex
        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group();
        }

        // If no JSON found, return the original response
        return response;
    }

    /**
     * Map issue severity to recommendation priority.
     *
     * @param severity The issue severity
     * @return The corresponding recommendation priority
     */
    private String mapSeverityToPriority(String severity) {
        return switch (severity) {
            case "critical" -> "high";
            case "major" -> "medium";
            case "minor" -> "low";
            default -> "medium";
        };
    }
}
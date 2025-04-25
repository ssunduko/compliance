package com.salesmsg.compliance.workflow.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.SubmissionImage;
import com.salesmsg.compliance.model.Verification;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.SubmissionImageRepository;
import com.salesmsg.compliance.service.AWSService;
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

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LangGraph node for verifying images in a 10DLC submission.
 * Analyzes opt-in form images and other consent-related imagery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageVerificationAgent {

    private final ComplianceSubmissionRepository submissionRepository;
    private final SubmissionImageRepository imageRepository;
    private final VerificationOrchestrator verificationOrchestrator;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String IMAGE_ANALYSIS_PROMPT = """
            You are an SMS Compliance Image Analyzer specialized in evaluating opt-in forms and consent mechanisms.
            Your task is to analyze the uploaded image for compliance with 10DLC regulations.
            
            Opt-In Type: %s
            Description: %s
            Image URL: %s
            
            Analyze the image for the following elements:
            1. Checkbox for SMS consent
            2. Clear consent text explaining messaging purpose
            3. Opt-out instructions (STOP keyword)
            4. Terms and conditions link or text
            5. Privacy policy reference
            6. Message frequency disclosure
            7. Clear, legible text
            8. Business identification
            
            Required elements for %s opt-in type:
            - Webform: checkbox, consent text, terms link, opt-out instructions
            - Paper form: checkbox, consent text, opt-out instructions
            - App screenshot: visible consent UI, terms link, opt-out instructions
            - Confirmation page: clear confirmation message, terms link, opt-out instructions
            
            Return your analysis in JSON format:
            {
              "hasRequiredElements": boolean,
              "detectedElements": [string],
              "missingElements": [string],
              "textQuality": "excellent" | "good" | "poor" | "unreadable",
              "complianceScore": float (0-100),
              "recommendations": [string]
            }
            """;

    /**
     * Process the images for compliance verification.
     *
     * @param state The current verification state
     * @return Updated state with image verification results
     */
    public Map<String, Object> process(Map<String, Object> state) {
        String verificationId = (String) state.get("verification_id");
        String submissionId = (String) state.get("submission_id");

        log.info("Processing image verification for submission: {}", submissionId);

        try {
            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 50, "images");

            // Get the submission
            ComplianceSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

            // Get images from the submission
            List<SubmissionImage> images = imageRepository.findBySubmissionId(submissionId);

            if (images.isEmpty()) {
                log.warn("No images found for submission: {}", submissionId);

                // Create a placeholder result with neutral score
                Map<String, Object> newState = new HashMap<>(state);

                @SuppressWarnings("unchecked")
                Map<String, Object> componentScores = (Map<String, Object>)
                        newState.computeIfAbsent("component_scores", k -> new HashMap<>());

                // No images means N/A for this component - using null instead of a low score
                componentScores.put("images", null);

                // Update verification progress
                verificationOrchestrator.updateVerificationProgress(verificationId, 60, "images");

                return newState;
            }

            // Initialize results
            float overallScore = 0.0f;
            List<Map<String, Object>> issues = new ArrayList<>();
            List<Map<String, Object>> recommendations = new ArrayList<>();

            // Analyze each image
            for (SubmissionImage image : images) {
                // Check if image has already been analyzed
                if (image.getComplianceScore() == null) {
                    // Image hasn't been analyzed yet - analyze it now
                    analyzeImage(image);
                }

                // Add image score to overall score
                if (image.getComplianceScore() != null) {
                    overallScore += image.getComplianceScore();
                }

                // Check for issues
                if (Boolean.FALSE.equals(image.getHasRequiredElements())) {
                    Map<String, Object> issue = new HashMap<>();
                    issue.put("component", "images");
                    issue.put("severity", "major");
                    issue.put("description", "Missing required elements in image: " +
                            (image.getMissingElements() != null ? image.getMissingElements() : "unknown elements"));
                    issues.add(issue);
                }

                // Add recommendations if available
                if (image.getRecommendations() != null && !image.getRecommendations().isEmpty()) {
                    Map<String, Object> recommendation = new HashMap<>();
                    recommendation.put("component", "images");
                    recommendation.put("priority", "medium");
                    recommendation.put("description", "Image improvements needed");
                    recommendation.put("action", image.getRecommendations());
                    recommendations.add(recommendation);
                }
            }

            // Calculate the average score
            if (!images.isEmpty()) {
                overallScore = overallScore / images.size();
            }

            // Update state with results
            Map<String, Object> newState = new HashMap<>(state);

            // Update component scores
            @SuppressWarnings("unchecked")
            Map<String, Object> componentScores = (Map<String, Object>)
                    newState.computeIfAbsent("component_scores", k -> new HashMap<>());
            componentScores.put("images", overallScore);

            // Add critical issues to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> criticalIssues = (List<Map<String, Object>>)
                    newState.computeIfAbsent("critical_issues", k -> new ArrayList<>());

            issues.stream()
                    .filter(issue -> "critical".equals(issue.get("severity")))
                    .forEach(criticalIssues::add);

            // Add recommendations to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allRecommendations = (List<Map<String, Object>>)
                    newState.computeIfAbsent("recommendations", k -> new ArrayList<>());
            allRecommendations.addAll(recommendations);

            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 60, "images");

            return newState;

        } catch (Exception e) {
            log.error("Error during image verification for submission: {}", submissionId, e);
            throw new RuntimeException("Image verification failed", e);
        }
    }

    /**
     * Analyze an image for compliance.
     *
     * @param image The image to analyze
     */
    private void analyzeImage(SubmissionImage image) {
        try {
            // Format the system prompt with image details
            String systemPrompt = String.format(
                    IMAGE_ANALYSIS_PROMPT,
                    image.getOptInType() != null ? image.getOptInType() : "unknown",
                    image.getDescription() != null ? image.getDescription() : "No description provided",
                    image.getImageUrl(),
                    image.getOptInType() != null ? image.getOptInType() : "unknown"
            );

            // Create the user message with the image URL
            String userPrompt = "Please analyze this image for 10DLC compliance.";

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

            // Update the image with analysis results
            updateImageWithAnalysisResults(image, analysisResult);

        } catch (Exception e) {
            log.error("Error analyzing image: {}", image.getId(), e);
            // Set default values for failed analysis
            image.setHasRequiredElements(false);
            image.setComplianceScore(0.0f);
            image.setRecommendations("Error analyzing image: " + e.getMessage());
            imageRepository.save(image);
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
                    "hasRequiredElements", false,
                    "detectedElements", List.of(),
                    "missingElements", List.of("Unable to analyze image"),
                    "textQuality", "unreadable",
                    "complianceScore", 0.0f,
                    "recommendations", List.of("Error analyzing image: " + e.getMessage())
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
     * Update the image entity with analysis results.
     *
     * @param image The image entity to update
     * @param result The analysis results
     */
    private void updateImageWithAnalysisResults(SubmissionImage image, Map<String, Object> result) {
        image.setHasRequiredElements((Boolean) result.get("hasRequiredElements"));

        @SuppressWarnings("unchecked")
        List<String> detectedElements = (List<String>) result.get("detectedElements");
        image.setDetectedElements(detectedElements != null ?
                String.join(", ", detectedElements) : null);

        @SuppressWarnings("unchecked")
        List<String> missingElements = (List<String>) result.get("missingElements");
        image.setMissingElements(missingElements != null ?
                String.join(", ", missingElements) : null);

        image.setTextQuality((String) result.get("textQuality"));
        image.setComplianceScore(((Number) result.get("complianceScore")).floatValue());

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) result.get("recommendations");
        image.setRecommendations(recommendations != null ?
                String.join("; ", recommendations) : null);

        imageRepository.save(image);
    }
}
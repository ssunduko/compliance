package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.ImageAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing images for 10DLC compliance.
 * Focuses on opt-in forms and other consent mechanisms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AWSService awsService;

    private static final String IMAGE_ANALYSIS_PROMPT = """
            You are an SMS Compliance Image Analyzer specialized in evaluating opt-in forms and consent mechanisms.
            Your task is to analyze the uploaded image for compliance with 10DLC regulations.
            
            Opt-In Type: %s
            Description: %s
            
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
     * Analyze an image for compliance with 10DLC requirements.
     *
     * @param image The image file to analyze
     * @param optInType The type of opt-in being analyzed
     * @param description Optional description of the image
     * @return The analysis results
     */
    public ImageAnalysisDTO analyzeImage(MultipartFile image, String optInType, String description) {
        log.info("Analyzing image for opt-in type: {}, size: {} bytes", optInType, image.getSize());

        try {
            // Upload image to S3 to get a URL
            String imageKey = "temp/analysis/" + UUID.randomUUID() + getFileExtension(image.getOriginalFilename());
            //TODO: Upload image
            //String imageUrl = awsService.uploadFile(image, imageKey);

            String imageUrl = "https://se498.s3.us-west-2.amazonaws.com/form.png";

            // Format the system prompt with request parameters
            String systemPrompt = String.format(
                    IMAGE_ANALYSIS_PROMPT,
                    optInType,
                    description != null ? description : "No description provided",
                    optInType
            );

            // Create the user message with the image URL
            String userPrompt = "Please analyze this image: " + imageUrl;

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

            // Delete the temporary image from S3
            //TODO: Delete Image
            //awsService.deleteFile(imageKey);

            // Parse the JSON response
            Map<String, Object> analysisResult = parseJsonResponse(responseText);

            // Build and return the DTO
            return buildImageAnalysisDTO(analysisResult, imageUrl, optInType, description);

        } catch (Exception e) {
            log.error("Error analyzing image", e);
            throw new RuntimeException("Failed to analyze image: " + e.getMessage(), e);
        }
    }

    /**
     * Build an ImageAnalysisDTO from the analysis result.
     *
     * @param analysisResult The raw analysis result
     * @param imageUrl The URL of the analyzed image
     * @param optInType The type of opt-in
     * @param description The image description
     * @return The formatted DTO
     */
    private ImageAnalysisDTO buildImageAnalysisDTO(
            Map<String, Object> analysisResult,
            String imageUrl,
            String optInType,
            String description) {

        // Extract values from the analysis result
        Boolean hasRequiredElements = (Boolean) analysisResult.getOrDefault("hasRequiredElements", false);

        @SuppressWarnings("unchecked")
        List<String> detectedElements = (List<String>) analysisResult.getOrDefault("detectedElements", new ArrayList<>());

        @SuppressWarnings("unchecked")
        List<String> missingElements = (List<String>) analysisResult.getOrDefault("missingElements", new ArrayList<>());

        String textQuality = (String) analysisResult.getOrDefault("textQuality", "unknown");

        Float complianceScore = ((Number) analysisResult.getOrDefault("complianceScore", 0)).floatValue();

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) analysisResult.getOrDefault("recommendations", new ArrayList<>());

        // Build the DTO
        return ImageAnalysisDTO.builder()
                .imageId(imageUrl)
                .optInType(optInType)
                .description(description)
                .hasRequiredElements(hasRequiredElements)
                .detectedElements(detectedElements)
                .missingElements(missingElements)
                .textQuality(textQuality)
                .complianceScore(complianceScore)
                .recommendations(recommendations)
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

    /**
     * Get the file extension from a filename.
     *
     * @param filename The filename
     * @return The file extension (with dot)
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty() || !filename.contains(".")) {
            return ".jpg"; // Default extension
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
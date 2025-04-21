package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.MessageValidationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for validating SMS messages against 10DLC compliance requirements.
 * Uses AI to analyze message content and provide recommendations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageValidationService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;


    private static final String MESSAGE_VALIDATION_PROMPT = """
            You are an SMS Message Compliance Expert for 10DLC campaigns. Your task is to evaluate
            if the provided sample messages comply with carrier requirements and SMS best practices.
            
            Business Type: %s
            Use Case: %s
            Should Include Phone Number: %s
            Should Include Links: %s
            
            Analyze each message and check for:
            1. Proper identification of the business/sender
            2. Clear opt-out instructions (STOP keyword)
            3. No prohibited content (gambling, adult content, etc.)
            4. Appropriate message length (message parts should be 160 characters or fewer)
            5. No excessive use of capital letters, exclamation points, or URLs
            6. No misleading or deceptive content
            7. Alignment with the stated use case
            8. Proper inclusion of phone numbers and links if specified
            
            Provide your analysis in JSON format with the following structure:
            {
              "overall_compliant": boolean,
              "overall_score": float (0-100),
              "message_results": [
                {
                  "text": string,
                  "compliant": boolean,
                  "matches_use_case": boolean,
                  "has_required_elements": boolean,
                  "missing_elements": [string],
                  "issues": [
                    {
                      "severity": "critical" | "major" | "minor",
                      "description": "string"
                    }
                  ],
                  "suggested_revision": string
                }
              ],
              "recommendations": [string]
            }
            """;

    /**
     * Validate a list of SMS messages for 10DLC compliance.
     *
     * @param request The validation request containing messages and context
     * @return The validation result with detailed analysis
     */
    public MessageValidationDTO validateMessages(MessageValidationDTO request) {
        log.info("Validating {} messages for use case: {}", request.getMessages().size(), request.getUseCase());

        try {
            // Format the system prompt with context
            String systemPrompt = String.format(
                    MESSAGE_VALIDATION_PROMPT,
                    request.getUseCase(),
                    request.getUseCase(),
                    Boolean.TRUE.equals(request.getShouldIncludePhoneNumber()) ? "Yes" : "No",
                    Boolean.TRUE.equals(request.getShouldIncludeLinks()) ? "Yes" : "No"
            );

            // Prepare the messages for analysis
            StringBuilder userMessageContent = new StringBuilder("Please analyze these sample messages for 10DLC compliance:\n\n");

            for (int i = 0; i < request.getMessages().size(); i++) {
                userMessageContent.append("Message ").append(i + 1).append(":\n");
                userMessageContent.append(request.getMessages().get(i)).append("\n\n");
            }

            // Create the prompt
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(systemPrompt));
            promptMessages.add(new UserMessage(userMessageContent.toString()));
            Prompt prompt = new Prompt(promptMessages);

            // Execute the chat completion
            ChatResponse response = chatClient
                    .prompt(prompt)
                    .call()
                    .chatResponse();

            String responseText = response.getResult().getOutput().getText();

            // Parse the response to JSON format
            Map<String, Object> analysisResult = parseMessageAnalysisResult(responseText);

            // Build the response DTO
            return buildValidationResponse(analysisResult, request.getMessages());

        } catch (Exception e) {
            log.error("Error validating messages", e);
            return buildErrorResponse("Failed to validate messages: " + e.getMessage());
        }
    }

    /**
     * Parse the JSON analysis result from the AI model response.
     *
     * @param responseText The text response from the AI model
     * @return A map containing the parsed analysis result
     */
    public Map<String, Object> parseMessageAnalysisResult(String responseText) {
        try {
            // Extract JSON from response (in case there's additional text around it)
            String jsonContent = extractJsonFromResponse(responseText);

            // Parse the JSON
            return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});

        } catch (JsonProcessingException e) {
            log.error("Error parsing message analysis result", e);
            return Map.of(
                    "overall_compliant", false,
                    "overall_score", 0,
                    "message_results", List.of(),
                    "recommendations", List.of("Error analyzing messages: " + e.getMessage())
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
     * Build a validation response DTO from the analysis result.
     *
     * @param analysisResult The parsed analysis result
     * @param originalMessages The original messages that were analyzed
     * @return The formatted validation response DTO
     */
    private MessageValidationDTO buildValidationResponse(Map<String, Object> analysisResult, List<String> originalMessages) {
        boolean overallCompliant = (boolean) analysisResult.getOrDefault("overall_compliant", false);
        float overallScore = ((Number) analysisResult.getOrDefault("overall_score", 0)).floatValue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messageResults = (List<Map<String, Object>>)
                analysisResult.getOrDefault("message_results", new ArrayList<>());

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>)
                analysisResult.getOrDefault("recommendations", new ArrayList<>());

        List<MessageValidationDTO.MessageResultDTO> resultDTOs = new ArrayList<>();

        // If the result doesn't include the original text, match by index
        if (messageResults.size() == originalMessages.size()) {
            for (int i = 0; i < messageResults.size(); i++) {
                Map<String, Object> result = messageResults.get(i);

                // If text is missing, use the original
                if (!result.containsKey("text")) {
                    result.put("text", originalMessages.get(i));
                }

                resultDTOs.add(convertToMessageResultDTO(result));
            }
        } else {
            // If the result count doesn't match, just use what we have
            for (Map<String, Object> result : messageResults) {
                resultDTOs.add(convertToMessageResultDTO(result));
            }
        }

        return MessageValidationDTO.builder()
                .overallCompliance(overallCompliant)
                .complianceScore(overallScore)
                .messageResults(resultDTOs)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Convert a message result map to a DTO.
     *
     * @param result The message result map
     * @return The message result DTO
     */
    private MessageValidationDTO.MessageResultDTO convertToMessageResultDTO(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<String> missingElements = (List<String>) result.getOrDefault("missing_elements", new ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>)
                result.getOrDefault("issues", new ArrayList<>());

        List<MessageValidationDTO.MessageIssueDTO> issueDTOs = issues.stream()
                .map(issue -> MessageValidationDTO.MessageIssueDTO.builder()
                        .severity((String) issue.get("severity"))
                        .description((String) issue.get("description"))
                        .build())
                .collect(Collectors.toList());

        return MessageValidationDTO.MessageResultDTO.builder()
                .text((String) result.get("text"))
                .compliant((Boolean) result.getOrDefault("compliant", false))
                .matchesUseCase((Boolean) result.getOrDefault("matches_use_case", false))
                .hasRequiredElements((Boolean) result.getOrDefault("has_required_elements", false))
                .missingElements(missingElements)
                .issues(issueDTOs)
                .suggestedRevision((String) result.get("suggested_revision"))
                .build();
    }

    /**
     * Build an error response DTO.
     *
     * @param errorMessage The error message
     * @return An error response DTO
     */
    private MessageValidationDTO buildErrorResponse(String errorMessage) {
        return MessageValidationDTO.builder()
                .overallCompliance(false)
                .complianceScore(0f)
                .messageResults(List.of())
                .recommendations(List.of(errorMessage))
                .build();
    }
}
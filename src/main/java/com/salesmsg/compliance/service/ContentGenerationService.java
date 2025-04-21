package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.MessageGenerationDTO;
import com.salesmsg.compliance.dto.UseCaseGenerationDTO;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for generating compliant content using AI.
 * Generates sample messages and use case descriptions that are compliant with 10DLC requirements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentGenerationService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String MESSAGE_GENERATION_PROMPT = """
            You are an SMS Compliance Content Generator specialized in creating compliant 10DLC message templates.
            Your task is to generate sample SMS message templates that follow carrier guidelines and best practices.
            
            Business Type: %s
            Use Case: %s
            Include Phone Numbers: %s
            Include Links: %s
            Message Count: %d
            
            Guidelines for generating compliant messages:
            1. Keep messages under 160 characters when possible
            2. Include proper business identification
            3. Include STOP opt-out instructions in all messages
            4. Avoid ALL CAPS and excessive punctuation!!!
            5. Use clear, concise language
            6. For placeholders, use double curly braces format: {{placeholder}}
            7. Common placeholders include: {{name}}, {{date}}, {{time}}, {{confirmation_code}}
            8. If phone numbers are requested, include a placeholder like {{phone}}
            9. If links are requested, include a placeholder like {{link}}
            10. Messages must be directly relevant to the stated use case
            
            Return your response in JSON format:
            {
              "messages": [
                "message 1 text",
                "message 2 text",
                ...
              ],
              "metadata": [
                {
                  "characterCount": int,
                  "segmentCount": int,
                  "hasOptOut": boolean,
                  "hasBusinessIdentifier": boolean
                },
                ...
              ]
            }
            """;

    private static final String USE_CASE_GENERATION_PROMPT = """
            You are a 10DLC Use Case Compliance Expert specialized in creating compliant use case descriptions.
            Your task is to generate a detailed use case description that follows carrier guidelines and best practices.
            
            Business Type: %s
            Messaging Purpose: %s
            Audience Type: %s
            Opt-In Method: %s
            
            Guidelines for generating a compliant use case description:
            1. Be specific about the types of messages that will be sent
            2. Clearly explain how customers opt in to receive messages
            3. Describe the frequency of messages (if applicable)
            4. Mention opt-out procedures
            5. Avoid vague language
            6. Be truthful and accurate about the messaging program
            7. Use clear, professional language
            8. Explain the value to the customer
            9. Mention any legal or regulatory compliance elements
            10. Keep the description between 200-500 words
            
            After generating the use case, conduct a quick compliance check on it.
            
            Return your response in JSON format:
            {
              "useCase": "detailed use case description...",
              "complianceCheck": {
                "isCompliant": boolean,
                "complianceScore": float (0-100),
                "feedback": "any feedback or suggestions"
              }
            }
            """;

    /**
     * Generate sample messages for a specific use case.
     *
     * @param request The generation request
     * @return The generated sample messages
     */
    public MessageGenerationDTO.Response generateSampleMessages(MessageGenerationDTO.Request request) {
        log.info("Generating {} sample messages for use case: {}", request.getMessageCount(), request.getUseCase());

        try {
            // Format the system prompt with request parameters
            String systemPrompt = String.format(
                    MESSAGE_GENERATION_PROMPT,
                    request.getBusinessType(),
                    request.getUseCase(),
                    Boolean.TRUE.equals(request.getIncludePhoneNumbers()) ? "Yes" : "No",
                    Boolean.TRUE.equals(request.getIncludeLinks()) ? "Yes" : "No",
                    request.getMessageCount() != null ? request.getMessageCount() : 3
            );

            // Create the user message
            String userPrompt = "Please generate compliant SMS message templates for this use case.";

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
            Map<String, Object> generationResult = parseJsonResponse(responseText);

            // Extract the messages
            @SuppressWarnings("unchecked")
            List<String> messages = (List<String>) generationResult.getOrDefault("messages", new ArrayList<>());

            // Extract the metadata if available
            List<MessageGenerationDTO.Response.MessageMetadata> metadata = new ArrayList<>();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metadataList = (List<Map<String, Object>>)
                    generationResult.getOrDefault("metadata", new ArrayList<>());

            if (metadataList != null && !metadataList.isEmpty()) {
                for (Map<String, Object> metaItem : metadataList) {
                    metadata.add(MessageGenerationDTO.Response.MessageMetadata.builder()
                            .characterCount(((Number) metaItem.getOrDefault("characterCount", 0)).intValue())
                            .segmentCount(((Number) metaItem.getOrDefault("segmentCount", 1)).intValue())
                            .hasOptOut((Boolean) metaItem.getOrDefault("hasOptOut", false))
                            .hasBusinessIdentifier((Boolean) metaItem.getOrDefault("hasBusinessIdentifier", false))
                            .build());
                }
            }

            // Build and return the response
            return MessageGenerationDTO.Response.builder()
                    .messages(messages)
                    .messageMetadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("Error generating sample messages", e);
            throw new RuntimeException("Failed to generate sample messages: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a use case description for a specific business and messaging purpose.
     *
     * @param request The generation request
     * @return The generated use case description
     */
    public UseCaseGenerationDTO.Response generateUseCase(UseCaseGenerationDTO.Request request) {
        log.info("Generating use case description for business type: {}, purpose: {}",
                request.getBusinessType(), request.getMessagingPurpose());

        try {
            // Format the system prompt with request parameters
            String systemPrompt = String.format(
                    USE_CASE_GENERATION_PROMPT,
                    request.getBusinessType(),
                    request.getMessagingPurpose(),
                    request.getAudienceType() != null ? request.getAudienceType() : "Customers",
                    request.getOptInMethod() != null ? request.getOptInMethod() : "Website form"
            );

            // Create the user message
            String userPrompt = "Please generate a compliant 10DLC use case description.";

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
            Map<String, Object> generationResult = parseJsonResponse(responseText);

            // Extract the use case description
            String useCase = (String) generationResult.getOrDefault("useCase", "");

            // Extract the compliance check if available
            UseCaseGenerationDTO.Response.ComplianceCheck complianceCheck = null;

            @SuppressWarnings("unchecked")
            Map<String, Object> checkResult = (Map<String, Object>) generationResult.get("complianceCheck");

            if (checkResult != null) {
                complianceCheck = UseCaseGenerationDTO.Response.ComplianceCheck.builder()
                        .isCompliant((Boolean) checkResult.getOrDefault("isCompliant", true))
                        .complianceScore(((Number) checkResult.getOrDefault("complianceScore", 85.0)).floatValue())
                        .feedback((String) checkResult.getOrDefault("feedback", ""))
                        .build();
            }

            // Build and return the response
            return UseCaseGenerationDTO.Response.builder()
                    .useCase(useCase)
                    .complianceCheck(complianceCheck)
                    .build();

        } catch (Exception e) {
            log.error("Error generating use case description", e);
            throw new RuntimeException("Failed to generate use case description: " + e.getMessage(), e);
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
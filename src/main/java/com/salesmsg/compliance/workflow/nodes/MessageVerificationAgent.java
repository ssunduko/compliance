package com.salesmsg.compliance.workflow.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.SubmissionMessage;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.SubmissionMessageRepository;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LangGraph node for validating the sample message templates in a 10DLC submission.
 * Analyzes messages for compliance with carrier requirements and 10DLC policies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageVerificationAgent {

    private final ComplianceSubmissionRepository submissionRepository;
    private final SubmissionMessageRepository messageRepository;
    private final VerificationOrchestrator verificationOrchestrator;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an SMS Message Compliance Expert for 10DLC campaigns. Your task is to evaluate
            if the provided sample messages comply with carrier requirements and SMS best practices.
            
            Business Type: %s
            Use Case: %s
            
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
                  "message_index": integer,
                  "compliant": boolean,
                  "matches_use_case": boolean,
                  "has_required_elements": boolean,
                  "missing_elements": [string],
                  "issues": [
                    {
                      "severity": "critical" | "major" | "minor",
                      "description": string
                    }
                  ],
                  "suggested_revision": string
                }
              ],
              "recommendations": [string]
            }
            """;

    public Map<String, Object> process(Map<String, Object> state) {
        String verificationId = (String) state.get("verification_id");
        String submissionId = (String) state.get("submission_id");

        log.info("Processing message verification for submission: {}", submissionId);

        try {
            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 25, "messages");

            // Get the submission and its messages
            ComplianceSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

            List<SubmissionMessage> messages = messageRepository.findBySubmissionId(submissionId);

            if (messages.isEmpty()) {
                log.warn("No messages found for submission: {}", submissionId);

                // Create a placeholder result with low score
                Map<String, Object> newState = new HashMap<>(state);

                @SuppressWarnings("unchecked")
                Map<String, Object> componentScores = (Map<String, Object>)
                        newState.computeIfAbsent("component_scores", k -> new HashMap<>());
                componentScores.put("messages", 0.0f);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> criticalIssues = (List<Map<String, Object>>)
                        newState.computeIfAbsent("critical_issues", k -> new ArrayList<>());

                Map<String, Object> criticalIssue = new HashMap<>();
                criticalIssue.put("component", "messages");
                criticalIssue.put("description", "No sample messages provided");
                criticalIssue.put("recommendation", "Add at least one sample message");
                criticalIssues.add(criticalIssue);

                // Update verification progress
                verificationOrchestrator.updateVerificationProgress(verificationId, 30, "messages");

                return newState;
            }

            // Format the messages for analysis
            List<String> messageTexts = messages.stream()
                    .map(SubmissionMessage::getMessageText)
                    .collect(Collectors.toList());

            // Build the system prompt
            String systemPrompt = String.format(
                    SYSTEM_PROMPT_TEMPLATE,
                    submission.getBusinessType(),
                    submission.getUseCase()
            );

            // Prepare the messages content
            StringBuilder userMessageContent = new StringBuilder("Please analyze these sample messages for 10DLC compliance:\n\n");

            for (int i = 0; i < messageTexts.size(); i++) {
                userMessageContent.append("Message ").append(i + 1).append(":\n");
                userMessageContent.append(messageTexts.get(i)).append("\n\n");
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

            // Parse the response
            Map<String, Object> analysisResult = parseMessageAnalysisResult(responseText);

            // Extract data
            boolean overallCompliant = (boolean) analysisResult.getOrDefault("overall_compliant", false);
            float overallScore = ((Number) analysisResult.getOrDefault("overall_score", 0)).floatValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messageResults = (List<Map<String, Object>>)
                    analysisResult.getOrDefault("message_results", new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<String> recommendations = (List<String>)
                    analysisResult.getOrDefault("recommendations", new ArrayList<>());

            // Update message records in the database
            updateMessageRecords(messages, messageResults);

            // Update state with results
            Map<String, Object> newState = new HashMap<>(state);

            // Update component scores
            @SuppressWarnings("unchecked")
            Map<String, Object> componentScores = (Map<String, Object>)
                    newState.computeIfAbsent("component_scores", k -> new HashMap<>());
            componentScores.put("messages", overallScore);

            newState.put("messages_compliant", overallCompliant);

            // Add critical issues to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> criticalIssues = (List<Map<String, Object>>)
                    newState.computeIfAbsent("critical_issues", k -> new ArrayList<>());

            for (Map<String, Object> messageResult : messageResults) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> issues = (List<Map<String, Object>>)
                        messageResult.getOrDefault("issues", new ArrayList<>());

                issues.stream()
                        .filter(issue -> "critical".equals(issue.get("severity")))
                        .forEach(issue -> {
                            Map<String, Object> criticalIssue = new HashMap<>();
                            criticalIssue.put("component", "messages");
                            criticalIssue.put("description", issue.get("description"));
                            criticalIssue.put("recommendation", "Revise message to address: " + issue.get("description"));
                            criticalIssues.add(criticalIssue);
                        });
            }

            // Add recommendations to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allRecommendations = (List<Map<String, Object>>)
                    newState.computeIfAbsent("recommendations", k -> new ArrayList<>());

            for (String recommendation : recommendations) {
                Map<String, Object> rec = new HashMap<>();
                rec.put("component", "messages");
                rec.put("priority", "medium");
                rec.put("description", recommendation);
                rec.put("action", recommendation);
                allRecommendations.add(rec);
            }

            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 40, "messages");

            return newState;

        } catch (Exception e) {
            log.error("Error during message verification for submission: {}", submissionId, e);
            throw new RuntimeException("Message verification failed", e);
        }
    }

    /**
     * Parse the JSON analysis result from the AI model response.
     *
     * @param responseText The text response from the AI model
     * @return A map containing the parsed analysis result
     */
    private Map<String, Object> parseMessageAnalysisResult(String responseText) {
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
     * Update the message records in the database with the analysis results.
     *
     * @param messages The list of message entities
     * @param messageResults The analysis results for each message
     */
    private void updateMessageRecords(List<SubmissionMessage> messages, List<Map<String, Object>> messageResults) {
        for (Map<String, Object> result : messageResults) {
            int index = ((Number) result.getOrDefault("message_index", 0)).intValue() - 1;

            if (index < 0 || index >= messages.size()) {
                log.warn("Invalid message index: {}, messages size: {}", index, messages.size());
                continue;
            }

            SubmissionMessage message = messages.get(index);

            message.setCompliant((Boolean) result.getOrDefault("compliant", false));
            message.setMatchesUseCase((Boolean) result.getOrDefault("matches_use_case", false));
            message.setHasRequiredElements((Boolean) result.getOrDefault("has_required_elements", false));

            @SuppressWarnings("unchecked")
            List<String> missingElements = (List<String>) result.get("missing_elements");
            message.setMissingElements(missingElements != null ? String.join(", ", missingElements) : null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issues = (List<Map<String, Object>>) result.getOrDefault("issues", List.of());
            if (!issues.isEmpty()) {
                List<String> issueDescriptions = issues.stream()
                        .map(issue -> issue.get("severity") + ": " + issue.get("description"))
                        .collect(Collectors.toList());
                message.setIssues(String.join("; ", issueDescriptions));
            }

            message.setSuggestedRevision((String) result.get("suggested_revision"));

            messageRepository.save(message);
        }
    }
}
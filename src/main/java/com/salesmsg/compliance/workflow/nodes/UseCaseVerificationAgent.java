package com.salesmsg.compliance.workflow.nodes;

import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.service.KendraService;
import com.salesmsg.compliance.service.UseCaseValidationService;
import com.salesmsg.compliance.workflow.VerificationOrchestrator;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LangGraph node for validating the use case description in a 10DLC submission.
 * Uses a combination of RAG (with Kendra) and LLM (with Claude model) to evaluate
 * the use case against compliance requirements.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UseCaseVerificationAgent {

    private final ComplianceSubmissionRepository submissionRepository;
    private final VerificationOrchestrator verificationOrchestrator;
    private final UseCaseValidationService useCaseValidationService;
    private final KendraService kendraService;
    private final ChatClient springAiChatClient;
    private final ChatLanguageModel langChainModel;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a Use Case Verification Expert for 10DLC SMS campaigns. Your task is to evaluate
            if the provided use case description is compliant with carrier requirements and guidelines.
            
            Business Name: {{businessName}}
            Business Type: {{businessType}}
            Use Case Description: {{useCase}}
            
            Carrier Guidelines:
            {{guidelines}}
            
            Instructions:
            1. Analyze if the use case is clear about the purpose of SMS messaging
            2. Check if the use case aligns with the business type
            3. Verify that the use case does not violate any carrier prohibitions
            4. Identify if the use case properly explains opt-in methods
            5. Evaluate if the use case accurately represents the messaging content
            
            Provide your analysis in JSON format with the following structure:
            {
              "is_compliant": boolean,
              "score": float (0-100),
              "issues": [
                {
                  "severity": "critical" | "major" | "minor",
                  "description": "string",
                  "recommendation": "string"
                }
              ],
              "recommendations": [
                {
                  "priority": "high" | "medium" | "low",
                  "description": "string",
                  "action": "string"
                }
              ],
              "reasoning": "string"
            }
            """;


    public Map<String, Object> process(Map<String, Object> state) {
        String verificationId = (String) state.get("verification_id");
        String submissionId = (String) state.get("submission_id");

        log.info("Processing use case verification for submission: {}", submissionId);

        try {
            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 10, "use_case");

            // Get the submission
            ComplianceSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

            // Retrieve carrier guidelines using Kendra RAG
            String guidelines = kendraService.retrieveCarrierGuidelines(submission.getBusinessType());

            // Build the prompt with business and use case context
            Map<String, Object> promptParams = new HashMap<>();
            promptParams.put("businessName", submission.getBusinessName());
            promptParams.put("businessType", submission.getBusinessType());
            promptParams.put("useCase", submission.getUseCase());
            promptParams.put("guidelines", guidelines);

            // Create the system prompt
            Message systemMessage = new SystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE)
                    .createMessage(promptParams);

            // Create the user message
            UserMessage userMessage = new UserMessage("Please analyze this use case for 10DLC compliance.");

            // Execute Spring AI chat completion
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));


            ChatResponse response = springAiChatClient
                    .prompt(prompt)
                    .call()
                    .chatResponse();

            String responseText = response.getResult().getOutput().getText();

            // Parse the response
            Map<String, Object> analysisResult = useCaseValidationService.parseAnalysisResult(responseText);

            // Extract data
            boolean isCompliant = (boolean) analysisResult.getOrDefault("is_compliant", false);
            float score = ((Number) analysisResult.getOrDefault("score", 0)).floatValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issues = (List<Map<String, Object>>)
                    analysisResult.getOrDefault("issues", new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendations = (List<Map<String, Object>>)
                    analysisResult.getOrDefault("recommendations", new ArrayList<>());

            // Update state with results
            Map<String, Object> newState = new HashMap<>(state);

            // Update component scores
            @SuppressWarnings("unchecked")
            Map<String, Object> componentScores = (Map<String, Object>)
                    newState.computeIfAbsent("component_scores", k -> new HashMap<>());
            componentScores.put("use_case", score);

            // Update overall score (will be recalculated after all components)
            newState.put("use_case_compliant", isCompliant);

            // Add critical issues to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> criticalIssues = (List<Map<String, Object>>)
                    newState.computeIfAbsent("critical_issues", k -> new ArrayList<>());

            issues.stream()
                    .filter(issue -> "critical".equals(issue.get("severity")))
                    .forEach(issue -> {
                        Map<String, Object> criticalIssue = new HashMap<>();
                        criticalIssue.put("component", "use_case");
                        criticalIssue.put("description", issue.get("description"));
                        criticalIssue.put("recommendation", issue.get("recommendation"));
                        criticalIssues.add(criticalIssue);
                    });

            // Add recommendations to the state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allRecommendations = (List<Map<String, Object>>)
                    newState.computeIfAbsent("recommendations", k -> new ArrayList<>());

            recommendations.forEach(rec -> {
                Map<String, Object> recommendation = new HashMap<>();
                recommendation.put("component", "use_case");
                recommendation.put("priority", rec.get("priority"));
                recommendation.put("description", rec.get("description"));
                recommendation.put("action", rec.get("action"));
                allRecommendations.add(recommendation);
            });

            // Update verification progress to complete this step
            verificationOrchestrator.updateVerificationProgress(verificationId, 20, "use_case");

            return newState;

        } catch (Exception e) {
            log.error("Error during use case verification for submission: {}", submissionId, e);
            throw new RuntimeException("Use case verification failed", e);
        }
    }
}
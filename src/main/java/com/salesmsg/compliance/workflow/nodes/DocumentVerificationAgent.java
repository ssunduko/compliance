package com.salesmsg.compliance.workflow.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.SubmissionDocument;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.SubmissionDocumentRepository;
import com.salesmsg.compliance.workflow.VerificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LangGraph node for verifying documents in a 10DLC submission.
 * Analyzes privacy policies, terms of service, and other compliance documents.
 */
@Component
@Slf4j
public class DocumentVerificationAgent {

    private final ComplianceSubmissionRepository submissionRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private final VerificationOrchestrator verificationOrchestrator;

    public DocumentVerificationAgent(
            ComplianceSubmissionRepository submissionRepository,
            SubmissionDocumentRepository documentRepository,
            ChatClient chatClient,
            ObjectMapper objectMapper,
            @Lazy VerificationOrchestrator verificationOrchestrator) {
        this.submissionRepository = submissionRepository;
        this.documentRepository = documentRepository;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.verificationOrchestrator = verificationOrchestrator;
    }

    private static final String DOCUMENT_VERIFICATION_PROMPT = """
            You are a Document Compliance Expert for 10DLC SMS campaigns. Your task is to evaluate
            if the provided documents meet carrier requirements and best practices.
            
            Business Type: %s
            Use Case: %s
            Document Type: %s
            Document Text: %s
            
            Analyze the document for the following elements based on document type:
            
            For Privacy Policy:
            1. Clear language about SMS data collection and usage
            2. Explanation of how consumer data is shared
            3. Opt-out instructions and consumer rights
            4. Contact information for privacy inquiries
            
            For Terms of Service:
            1. Clear explanation of SMS services provided
            2. Frequency of messages
            3. Message and data rates disclosure
            4. Opt-out instructions
            
            Return your analysis in JSON format:
            {
              "compliant": boolean,
              "score": float (0-100),
              "has_required_elements": boolean,
              "detected_elements": [string],
              "missing_elements": [string],
              "content_issues": [string],
              "recommendations": [string]
            }
            """;

    /**
     * Process the documents for compliance verification.
     *
     * @param state The current verification state
     * @return Updated state with document verification results
     */
    public Map<String, Object> process(Map<String, Object> state) {
        String verificationId = (String) state.get("verification_id");
        String submissionId = (String) state.get("submission_id");

        log.info("Processing document verification for submission: {}", submissionId);

        try {
            // Update verification progress
            verificationOrchestrator.updateVerificationProgress(verificationId, 80, "documents");

            // Get the submission
            ComplianceSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

            // Get documents from the submission
            List<SubmissionDocument> documents = documentRepository.findBySubmissionId(submissionId);

            if (documents.isEmpty()) {
                log.warn("No documents found for submission: {}", submissionId);

                // Create a placeholder result with neutral score
                Map<String, Object> newState = new HashMap<>(state);

                @SuppressWarnings("unchecked")
                Map<String, Object> componentScores = (Map<String, Object>)
                        newState.computeIfAbsent("component_scores", k -> new HashMap<>());

                // No documents means N/A for this component - using null instead of a low score
                componentScores.put("documents", null);

                // Update verification progress
                verificationOrchestrator.updateVerificationProgress(verificationId, 90, "documents");

                return newState;
            }

            // Initialize results
            float overallScore = 0.0f;
            List<Map<String, Object>> issues = new ArrayList<>();
            List<Map<String, Object>> recommendations = new ArrayList<>();

            // Analyze each document
            for (SubmissionDocument document : documents) {
                // Check if document has already been analyzed
                if (document.getComplianceScore() == null) {
                    // Document hasn't been analyzed yet - analyze it now
                    analyzeDocument(document, submission);
                }

                // Add document score to overall score
                if (document.getComplianceScore() != null) {
                    overallScore += document.getComplianceScore();
                }

                // Check for issues
                if (Boolean.FALSE.equals(document.getCompliant())) {
                    Map<String, Object> issue = new HashMap<>();
                    issue.put("component", "documents");
                    issue.put("severity", "major");
                    issue.put("description", "Non-compliant document: " + document.getDocumentType());
                    if (document.getContentIssues() != null) {
                        issue.put("description", issue.get("description") + " - " + document.getContentIssues());
                    }
                    issues.add(issue);
                }

                // Add recommendations if available
                if (document.getRecommendations() != null && !document.getRecommendations().isEmpty()) {
                    Map<String, Object> recommendation = new HashMap<>();
                    recommendation.put("component", "documents");
                    recommendation.put("priority", "medium");
                    recommendation.put("description", "Document improvements needed: " + document.getDocumentType());
                    recommendation.put("action", document.getRecommendations());
                    recommendations.add(recommendation);
                }
            }

            // Calculate the average score
            if (!documents.isEmpty()) {
                overallScore = overallScore / documents.size();
            }

            // Update state with results
            Map<String, Object> newState = new HashMap<>(state);

            // Update component scores
            @SuppressWarnings("unchecked")
            Map<String, Object> componentScores = (Map<String, Object>)
                    newState.computeIfAbsent("component_scores", k -> new HashMap<>());
            componentScores.put("documents", overallScore);

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
            verificationOrchestrator.updateVerificationProgress(verificationId, 90, "documents");

            return newState;

        } catch (Exception e) {
            log.error("Error during document verification for submission: {}", submissionId, e);
            throw new RuntimeException("Document verification failed", e);
        }
    }

    /**
     * Analyze a document for compliance.
     *
     * @param document The document to analyze
     * @param submission The submission context
     */
    private void analyzeDocument(SubmissionDocument document, ComplianceSubmission submission) {
        try {
            // Get the document text - in a real implementation, you would extract text from the document
            // This could involve downloading from S3, parsing PDF/DOC, etc.
            String documentText = document.getExtractedText();

            // If we don't have extracted text, we'd need to get it
            if (documentText == null || documentText.isEmpty()) {
                log.warn("No extracted text for document: {}", document.getId());
                documentText = "Document text not available";
            }

            // Format the system prompt with document details
            String systemPrompt = String.format(
                    DOCUMENT_VERIFICATION_PROMPT,
                    submission.getBusinessType(),
                    submission.getUseCase(),
                    document.getDocumentType(),
                    documentText.length() > 2000 ?
                            documentText.substring(0, 2000) + "...[content truncated]..." : documentText
            );

            // Create the user message
            String userPrompt = "Please analyze this document for 10DLC compliance.";

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

            // Update the document with analysis results
            updateDocumentWithAnalysisResults(document, analysisResult);

        } catch (Exception e) {
            log.error("Error analyzing document: {}", document.getId(), e);
            // Set default values for failed analysis
            document.setCompliant(false);
            document.setComplianceScore(0.0f);
            document.setContentIssues("Error analyzing document: " + e.getMessage());
            documentRepository.save(document);
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
                    "compliant", false,
                    "score", 0.0f,
                    "has_required_elements", false,
                    "detected_elements", List.of(),
                    "missing_elements", List.of("Unable to analyze document"),
                    "content_issues", List.of("Error parsing AI response: " + e.getMessage()),
                    "recommendations", List.of("Please try again or contact support")
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
     * Update the document entity with analysis results.
     *
     * @param document The document entity
     * @param result The analysis results
     */
    private void updateDocumentWithAnalysisResults(SubmissionDocument document, Map<String, Object> result) {
        document.setCompliant((Boolean) result.get("compliant"));
        document.setComplianceScore(((Number) result.get("score")).floatValue());
        document.setHasRequiredElements((Boolean) result.get("has_required_elements"));

        @SuppressWarnings("unchecked")
        List<String> detectedElements = (List<String>) result.get("detected_elements");
        document.setDetectedElements(detectedElements != null && !detectedElements.isEmpty() ?
                String.join(", ", detectedElements) : null);

        @SuppressWarnings("unchecked")
        List<String> missingElements = (List<String>) result.get("missing_elements");
        document.setMissingElements(missingElements != null && !missingElements.isEmpty() ?
                String.join(", ", missingElements) : null);

        @SuppressWarnings("unchecked")
        List<String> contentIssues = (List<String>) result.get("content_issues");
        document.setContentIssues(contentIssues != null && !contentIssues.isEmpty() ?
                String.join("; ", contentIssues) : null);

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) result.get("recommendations");
        document.setRecommendations(recommendations != null && !recommendations.isEmpty() ?
                String.join("; ", recommendations) : null);

        documentRepository.save(document);
    }
}
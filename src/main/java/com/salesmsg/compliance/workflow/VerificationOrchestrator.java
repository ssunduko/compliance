package com.salesmsg.compliance.workflow;

import com.salesmsg.compliance.model.ComplianceReport;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.Verification;
import com.salesmsg.compliance.repository.ComplianceReportRepository;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.VerificationRepository;
import com.salesmsg.compliance.service.KendraService;
import com.salesmsg.compliance.workflow.nodes.DocumentVerificationAgent;
import com.salesmsg.compliance.workflow.nodes.MessageVerificationAgent;
import com.salesmsg.compliance.workflow.nodes.UseCaseVerificationAgent;
import com.salesmsg.compliance.workflow.nodes.WebsiteVerificationAgent;
import com.salesmsg.compliance.workflow.nodes.ImageVerificationAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates the verification workflow for 10DLC compliance submissions
 * using the orchestrator-workers pattern.
 * The orchestrator analyzes the submission and dynamically determines which workers
 * to run based on the submission content.
 */
@Service
@Slf4j
public class VerificationOrchestrator {

    private final ChatClient chatClient;
    private final ComplianceSubmissionRepository submissionRepository;
    private final VerificationRepository verificationRepository;
    private final ComplianceReportRepository reportRepository;

    private final UseCaseVerificationAgent useCaseAgent;
    private final MessageVerificationAgent messageAgent;
    private final ImageVerificationAgent imageAgent;
    private final WebsiteVerificationAgent websiteAgent;
    private final DocumentVerificationAgent documentAgent;

    public VerificationOrchestrator(
            ChatClient chatClient,
            ComplianceSubmissionRepository submissionRepository,
            VerificationRepository verificationRepository,
            ComplianceReportRepository reportRepository,
            KendraService kendraService,
            @Lazy UseCaseVerificationAgent useCaseAgent,
            @Lazy MessageVerificationAgent messageAgent,
            @Lazy ImageVerificationAgent imageAgent,
            @Lazy WebsiteVerificationAgent websiteAgent,
            @Lazy DocumentVerificationAgent documentAgent) {

        this.chatClient = chatClient;
        this.submissionRepository = submissionRepository;
        this.verificationRepository = verificationRepository;
        this.reportRepository = reportRepository;
        this.useCaseAgent = useCaseAgent;
        this.messageAgent = messageAgent;
        this.imageAgent = imageAgent;
        this.websiteAgent = websiteAgent;
        this.documentAgent = documentAgent;
    }

    private static final Duration ESTIMATED_VERIFICATION_TIME = Duration.ofMinutes(15);

    private static final String ORCHESTRATOR_PROMPT = """
            You are an SMS Compliance Verification Expert for 10DLC campaigns.
            Analyze this compliance submission and determine which verification steps need to be performed.
            
            Business Name: {businessName}
            Business Type: {businessType}
            Use Case: {useCase}
            Website URL: {websiteUrl}
            Opt-in Method: {optInMethod}
            Has Sample Messages: {hasSampleMessages}
            Has Images: {hasImages}
            Has Documents: {hasDocuments}
            
            Determine which components need verification based on the submission content:
            
            Return your response in this JSON format:
            {
              "analysis": "Explain your understanding of the submission and verification needs",
              "verificationSteps": [
                {
                  "component": "use_case",
                  "priority": "high",
                  "reason": "All submissions require use case verification"
                },
                {
                  "component": "messages",
                  "priority": "high",
                  "reason": "Sample messages need to be verified for compliance"
                },
                {
                  "component": "website",
                  "priority": "medium",
                  "reason": "Only if website URL is provided"
                },
                {
                  "component": "images",
                  "priority": "medium",
                  "reason": "Only if opt-in images are provided"
                },
                {
                  "component": "documents",
                  "priority": "low",
                  "reason": "Only if compliance documents are provided"
                }
              ]
            }
            """;

    /**
     * Represents a verification step identified by the orchestrator.
     */
    public record VerificationStep(String component, String priority, String reason) {}

    /**
     * Response from the orchestrator containing verification analysis and steps.
     */
    public record OrchestratorResponse(String analysis, List<VerificationStep> verificationSteps) {}

    /**
     * Start the verification process for a submission.
     *
     * @param submissionId The ID of the submission to verify
     * @return The created verification object
     */
    @Transactional
    public Verification startVerification(String submissionId) {
        ComplianceSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));

        // Create a new verification record
        Verification verification = Verification.builder()
                .submissionId(submissionId)
                .status(Verification.VerificationStatus.PENDING)
                .progress(0)
                .completedSteps(new ArrayList<>())
                .currentStep("planning")
                .estimatedCompletionTime(LocalDateTime.now().plus(ESTIMATED_VERIFICATION_TIME))
                .build();

        verification = verificationRepository.save(verification);

        // Update submission status
        submission.setStatus(ComplianceSubmission.SubmissionStatus.VERIFYING);
        submission.setVerificationId(verification.getId());
        submissionRepository.save(submission);

        // Start the verification process asynchronously
        Verification finalVerification = verification;
        CompletableFuture.runAsync(() -> executeVerificationWorkflow(finalVerification, submission));

        return verification;
    }

    /**
     * Execute the verification workflow using the orchestrator-workers pattern.
     *
     * @param verification The verification record
     * @param submission The submission to verify
     */
    void executeVerificationWorkflow(Verification verification, ComplianceSubmission submission) {
        String verificationId = verification.getId();

        try {
            // Update verification status to running
            updateVerificationStatus(verificationId, Verification.VerificationStatus.RUNNING);
            updateVerificationProgress(verificationId, 5, "planning");

            // Get orchestrator analysis to determine verification steps
            OrchestratorResponse orchestratorResponse = getVerificationPlan(submission);
            log.info("Verification plan for submission {}: {} steps identified",
                    submission.getId(), orchestratorResponse.verificationSteps().size());

            // Initialize the state that will be passed between workers
            Map<String, Object> state = initializeState(submission, verification);

            // Execute each verification step with appropriate worker
            int totalSteps = orchestratorResponse.verificationSteps().size();
            int completedSteps = 0;

            for (VerificationStep step : orchestratorResponse.verificationSteps()) {
                // Update current step
                updateVerificationProgress(
                        verificationId,
                        5 + (90 * completedSteps / totalSteps),
                        step.component()
                );

                // Execute appropriate worker based on component
                state = executeWorker(step.component(), state);

                // Update progress
                completedSteps++;
                updateVerificationProgress(
                        verificationId,
                        5 + (90 * completedSteps / totalSteps),
                        step.component()
                );
            }

            // Process the final state to create compliance report
            processWorkflowResult(verificationId, state);

        } catch (Exception e) {
            log.error("Verification workflow failed for submission: {}", submission.getId(), e);

            // Update verification status to failed
            Verification failedVerification = verificationRepository.findById(verificationId)
                    .orElseThrow(() -> new IllegalStateException("Verification not found: " + verificationId));

            failedVerification.setStatus(Verification.VerificationStatus.FAILED);
            failedVerification.setErrorCode("workflow_error");
            failedVerification.setErrorMessage("Verification workflow failed: " + e.getMessage());
            failedVerification.setCompletedAt(LocalDateTime.now());
            verificationRepository.save(failedVerification);

            // Update submission status back to submitted
            submission.setStatus(ComplianceSubmission.SubmissionStatus.SUBMITTED);
            submissionRepository.save(submission);
        }
    }

    /**
     * Initialize the state map with submission information.
     *
     * @param submission The submission being verified
     * @param verification The verification record
     * @return Initial state map
     */
    private Map<String, Object> initializeState(ComplianceSubmission submission, Verification verification) {
        Map<String, Object> state = new HashMap<>();

        // Add submission and verification IDs
        state.put("submission_id", submission.getId());
        state.put("verification_id", verification.getId());

        // Add submission details
        state.put("business_name", submission.getBusinessName());
        state.put("business_type", submission.getBusinessType());
        state.put("use_case", submission.getUseCase());
        state.put("website_url", submission.getWebsiteUrl());
        state.put("opt_in_method", submission.getOptInMethod());

        // Initialize component scores map
        state.put("component_scores", new HashMap<String, Float>());

        // Initialize critical issues list
        state.put("critical_issues", new ArrayList<Map<String, Object>>());

        // Initialize recommendations list
        state.put("recommendations", new ArrayList<Map<String, Object>>());

        return state;
    }

    /**
     * Get verification plan from the orchestrator.
     *
     * @param submission The submission to analyze
     * @return Orchestrator response with verification steps
     */
    public OrchestratorResponse getVerificationPlan(ComplianceSubmission submission) {
        Assert.notNull(submission, "Submission must not be null");

        // Check what content the submission has
        boolean hasSampleMessages = !submission.getSampleMessages().isEmpty();
        boolean hasImages = !submission.getImages().isEmpty();
        boolean hasDocuments = !submission.getDocuments().isEmpty();

        return chatClient.prompt()
                .user(u -> u.text(ORCHESTRATOR_PROMPT)
                        .param("businessName", submission.getBusinessName())
                        .param("businessType", submission.getBusinessType())
                        .param("useCase", submission.getUseCase())
                        .param("websiteUrl", submission.getWebsiteUrl() != null ? submission.getWebsiteUrl() : "Not provided")
                        .param("optInMethod", submission.getOptInMethod() != null ? submission.getOptInMethod() : "Not specified")
                        .param("hasSampleMessages", Boolean.toString(hasSampleMessages))
                        .param("hasImages", Boolean.toString(hasImages))
                        .param("hasDocuments", Boolean.toString(hasDocuments))
                )
                .call()
                .entity(OrchestratorResponse.class);
    }

    /**
     * Execute the appropriate worker for a verification step.
     *
     * @param component The component to verify
     * @param state The current state
     * @return Updated state after worker execution
     */
    public Map<String, Object> executeWorker(String component, Map<String, Object> state) {
        log.info("Executing worker for component: {}", component);

        return switch (component) {
            case "use_case" -> useCaseAgent.process(state);
            case "messages" -> messageAgent.process(state);
            case "images" -> imageAgent.process(state);
            case "website" -> websiteAgent.process(state);
            case "documents" -> documentAgent.process(state);
            default -> {
                log.warn("Unknown verification component: {}", component);
                yield state;
            }
        };
    }

    /**
     * Process the workflow result and create a compliance report.
     *
     * @param verificationId The verification ID
     * @param result The workflow result
     */
    @Transactional
    public void processWorkflowResult(String verificationId, Map<String, Object> result) {
        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalStateException("Verification not found: " + verificationId));

        String submissionId = verification.getSubmissionId();
        ComplianceSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

        // Update verification as completed
        verification.setStatus(Verification.VerificationStatus.COMPLETED);
        verification.setProgress(100);
        verification.setCompletedAt(LocalDateTime.now());
        verification = verificationRepository.save(verification);

        // Extract data from workflow result
        Float overallScore = calculateOverallScore(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> componentScores = (Map<String, Object>) result.getOrDefault("component_scores", Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> criticalIssues = (List<Map<String, Object>>) result.getOrDefault("critical_issues", List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) result.getOrDefault("recommendations", List.of());

        // Create the compliance report
        ComplianceReport report = ComplianceReport.builder()
                .submissionId(submissionId)
                .overallScore(overallScore)
                .approvalLikelihood(determineApprovalLikelihood(overallScore))
                .useCaseScore(extractScore(componentScores, "use_case"))
                .messagesScore(extractScore(componentScores, "messages"))
                .imagesScore(extractScore(componentScores, "images"))
                .websiteScore(extractScore(componentScores, "website"))
                .documentsScore(extractScore(componentScores, "documents"))
                .criticalIssues(convertCriticalIssues(criticalIssues))
                .recommendations(convertRecommendations(recommendations))
                .build();

        reportRepository.save(report);

        // Update submission with compliance score and status
        submission.setComplianceScore(overallScore);
        submission.setStatus(ComplianceSubmission.SubmissionStatus.VERIFIED);
        submissionRepository.save(submission);

        log.info("Verification completed for submission {}: score={}", submissionId, overallScore);
    }

    /**
     * Calculate the overall compliance score based on component scores.
     *
     * @param result The workflow result
     * @return The overall compliance score
     */
    private Float calculateOverallScore(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> componentScores = (Map<String, Object>) result.getOrDefault("component_scores", Map.of());

        // Define component weights
        Map<String, Float> weights = Map.of(
                "use_case", 0.25f,
                "messages", 0.25f,
                "images", 0.20f,
                "website", 0.20f,
                "documents", 0.10f
        );

        float totalScore = 0.0f;
        float totalWeight = 0.0f;

        for (Map.Entry<String, Object> entry : componentScores.entrySet()) {
            String component = entry.getKey();
            Float weight = weights.getOrDefault(component, 0.0f);
            Float score = entry.getValue() instanceof Number ?
                    ((Number) entry.getValue()).floatValue() : 0.0f;

            totalScore += score * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? totalScore / totalWeight : 0.0f;
    }

    /**
     * Update the status of a verification.
     *
     * @param verificationId The verification ID
     * @param status The new status
     */
    @Transactional
    public void updateVerificationStatus(String verificationId, Verification.VerificationStatus status) {
        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalStateException("Verification not found: " + verificationId));

        verification.setStatus(status);
        verificationRepository.save(verification);
    }

    /**
     * Update the progress and current step of a verification.
     *
     * @param verificationId The verification ID
     * @param progress The progress percentage (0-100)
     * @param currentStep The current step name
     */
    @Transactional
    public void updateVerificationProgress(String verificationId, Integer progress, String currentStep) {
        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalStateException("Verification not found: " + verificationId));

        verification.setProgress(progress);
        verification.setCurrentStep(currentStep);

        if (!verification.getCompletedSteps().contains(currentStep)) {
            verification.getCompletedSteps().add(currentStep);
        }

        verificationRepository.save(verification);
    }

    /**
     * Determine the approval likelihood based on the overall compliance score.
     *
     * @param score The overall compliance score
     * @return The approval likelihood
     */
    private ComplianceReport.ApprovalLikelihood determineApprovalLikelihood(Float score) {
        if (score >= 85) {
            return ComplianceReport.ApprovalLikelihood.HIGH;
        } else if (score >= 65) {
            return ComplianceReport.ApprovalLikelihood.MEDIUM;
        } else {
            return ComplianceReport.ApprovalLikelihood.LOW;
        }
    }

    /**
     * Extract a component score from the component scores map.
     *
     * @param componentScores The component scores map
     * @param component The component name
     * @return The component score or null if not present
     */
    private Float extractScore(Map<String, Object> componentScores, String component) {
        Object score = componentScores.get(component);
        if (score instanceof Number) {
            return ((Number) score).floatValue();
        }
        return null;
    }

    /**
     * Convert critical issues from the workflow result to the model format.
     *
     * @param criticalIssues The critical issues from the workflow result
     * @return The converted critical issues
     */
    private List<ComplianceReport.CriticalIssue> convertCriticalIssues(List<Map<String, Object>> criticalIssues) {
        return criticalIssues.stream()
                .map(issue -> ComplianceReport.CriticalIssue.builder()
                        .component((String) issue.get("component"))
                        .description((String) issue.get("description"))
                        .recommendation((String) issue.get("recommendation"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Convert recommendations from the workflow result to the model format.
     *
     * @param recommendations The recommendations from the workflow result
     * @return The converted recommendations
     */
    private List<ComplianceReport.Recommendation> convertRecommendations(List<Map<String, Object>> recommendations) {
        return recommendations.stream()
                .map(rec -> ComplianceReport.Recommendation.builder()
                        .component((String) rec.get("component"))
                        .priority(parseRecommendationPriority((String) rec.get("priority")))
                        .description((String) rec.get("description"))
                        .action((String) rec.get("action"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Parse a recommendation priority string to the enum value.
     *
     * @param priority The priority string
     * @return The recommendation priority enum value
     */
    private ComplianceReport.RecommendationPriority parseRecommendationPriority(String priority) {
        if (priority == null) {
            return ComplianceReport.RecommendationPriority.MEDIUM;
        }

        try {
            return ComplianceReport.RecommendationPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ComplianceReport.RecommendationPriority.MEDIUM;
        }
    }
}
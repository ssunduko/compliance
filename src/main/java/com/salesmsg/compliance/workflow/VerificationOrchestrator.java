package com.salesmsg.compliance.workflow;

import com.salesmsg.compliance.model.ComplianceReport;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.Verification;
import com.salesmsg.compliance.repository.ComplianceReportRepository;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.VerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates the verification workflow for 10DLC compliance submissions.
 * Manages the execution of the LangGraph verification workflow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationOrchestrator {

    private final Map<String, Object> initialVerificationState;
    private final ComplianceSubmissionRepository submissionRepository;
    private final VerificationRepository verificationRepository;
    private final ComplianceReportRepository reportRepository;

    private static final Duration ESTIMATED_VERIFICATION_TIME = Duration.ofMinutes(15);
    private static final Duration VERIFICATION_TIMEOUT = Duration.ofMinutes(30);

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
                .currentStep("use_case")
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
     * Execute the verification workflow using LangGraph.
     *
     * @param verification The verification record
     * @param submission The submission to verify
     */
    private void executeVerificationWorkflow(Verification verification, ComplianceSubmission submission) {
        String verificationId = verification.getId();

        try {
            // Update verification status to running
            updateVerificationStatus(verificationId, Verification.VerificationStatus.RUNNING);

            // Prepare the input state for the workflow
            Map<String, Object> inputState = new HashMap<>(initialVerificationState);
            inputState.put("submission_id", submission.getId());
            inputState.put("verification_id", verificationId);
            inputState.put("business_name", submission.getBusinessName());
            inputState.put("business_type", submission.getBusinessType());
            inputState.put("use_case", submission.getUseCase());
            inputState.put("website_url", submission.getWebsiteUrl());
            inputState.put("opt_in_method", submission.getOptInMethod());

            // Execute the workflow (this will run through all steps in the graph)
            //Map<String, Object> result = verificationWorkflowExecutor.execute(inputState);

            Map<String, Object> result = null;

            // Process successful result
            processWorkflowResult(verificationId, result);

        } catch (Exception e) {
            log.error("Verification workflow failed for submission: {}", submission.getId(), e);

            // Update verification status to failed
            Verification failedVerification = verificationRepository.findById(verificationId)
                    .orElseThrow(() -> new IllegalStateException("Verification not found: " + verificationId));

            failedVerification.setStatus(Verification.VerificationStatus.FAILED);
            failedVerification.setErrorCode("workflow_error");
            failedVerification.setErrorMessage("Verification workflow failed: " + e.getMessage());
            verificationRepository.save(failedVerification);

            // Update submission status back to submitted
            submission.setStatus(ComplianceSubmission.SubmissionStatus.SUBMITTED);
            submissionRepository.save(submission);
        }
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
        Float overallScore = ((Number) result.getOrDefault("overall_score", 0)).floatValue();

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
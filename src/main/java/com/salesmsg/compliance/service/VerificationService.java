package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.ComplianceReportDTO;
import com.salesmsg.compliance.dto.VerificationDTO;
import com.salesmsg.compliance.exception.ResourceNotFoundException;
import com.salesmsg.compliance.model.ComplianceReport;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.Verification;
import com.salesmsg.compliance.repository.ComplianceReportRepository;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.VerificationRepository;
import com.salesmsg.compliance.workflow.VerificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing the verification process for 10DLC compliance submissions.
 * Handles starting verifications, checking status, and retrieving compliance reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final ComplianceSubmissionRepository submissionRepository;
    private final ComplianceReportRepository reportRepository;
    private final VerificationOrchestrator verificationOrchestrator;

    /**
     * Start a verification process for a submission.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     * @return The verification details
     */
    @Transactional
    public VerificationDTO startVerification(String submissionId, String userId) {
        log.info("Starting verification for submission: {}, user: {}", submissionId, userId);

        // Get the submission with access check
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Check if the submission can be verified
        if (submission.getStatus() == ComplianceSubmission.SubmissionStatus.VERIFYING) {
            log.warn("Submission is already being verified: {}", submissionId);

            // Return the current verification status
            Verification verification = verificationRepository.findById(submission.getVerificationId())
                    .orElseThrow(() -> new IllegalStateException("Verification not found: " + submission.getVerificationId()));

            return mapVerificationToDto(verification);
        }

        if (submission.getStatus() != ComplianceSubmission.SubmissionStatus.DRAFT &&
                submission.getStatus() != ComplianceSubmission.SubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("Submission cannot be verified in its current status: " + submission.getStatus());
        }

        // Start the verification process
        Verification verification = verificationOrchestrator.startVerification(submissionId);

        return mapVerificationToDto(verification);
    }

    /**
     * Get the status of a verification.
     *
     * @param verificationId The verification ID
     * @param userId The user ID of the requester
     * @return The verification status
     */
    @Transactional(readOnly = true)
    public VerificationDTO getVerificationStatus(String verificationId, String userId) {
        log.info("Getting verification status: {}, user: {}", verificationId, userId);

        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + verificationId));

        // Get the submission to check access
        String submissionId = verification.getSubmissionId();
        getSubmissionWithAccessCheck(submissionId, userId);

        return mapVerificationToDto(verification);
    }

    /**
     * Get the compliance report for a submission.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     * @return The compliance report
     */
    @Transactional(readOnly = true)
    public ComplianceReportDTO getComplianceReport(String submissionId, String userId) {
        log.info("Getting compliance report for submission: {}, user: {}", submissionId, userId);

        // Get the submission with access check
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Check if the submission has been verified
        if (submission.getStatus() != ComplianceSubmission.SubmissionStatus.VERIFIED &&
                submission.getStatus() != ComplianceSubmission.SubmissionStatus.SUBMITTED &&
                submission.getStatus() != ComplianceSubmission.SubmissionStatus.APPROVED &&
                submission.getStatus() != ComplianceSubmission.SubmissionStatus.REJECTED) {
            throw new IllegalStateException("Compliance report not available: submission has not been verified");
        }

        // Get the report
        ComplianceReport report = reportRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Compliance report not found for submission: " + submissionId));

        return mapReportToDto(report);
    }

    /**
     * Cancel an ongoing verification.
     *
     * @param verificationId The verification ID
     * @param userId The user ID of the requester
     */
    @Transactional
    public void cancelVerification(String verificationId, String userId) {
        log.info("Cancelling verification: {}, user: {}", verificationId, userId);

        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + verificationId));

        // Get the submission to check access
        String submissionId = verification.getSubmissionId();
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Check if the verification can be cancelled
        if (verification.getStatus() != Verification.VerificationStatus.PENDING &&
                verification.getStatus() != Verification.VerificationStatus.RUNNING) {
            throw new IllegalStateException("Verification cannot be cancelled in its current status: " + verification.getStatus());
        }

        // Update verification status
        verification.setStatus(Verification.VerificationStatus.FAILED);
        verification.setErrorCode("cancelled_by_user");
        verification.setErrorMessage("Verification was cancelled by the user");
        verificationRepository.save(verification);

        // Update submission status back to submitted
        submission.setStatus(ComplianceSubmission.SubmissionStatus.SUBMITTED);
        submissionRepository.save(submission);

        log.info("Verification cancelled: {}", verificationId);
    }

    /**
     * Get a submission with access check.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     * @return The submission
     */
    private ComplianceSubmission getSubmissionWithAccessCheck(String submissionId, String userId) {
        ComplianceSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

        // Check user access
        if (!submission.getUserId().equals(userId)) {
            throw new AccessDeniedException("User does not have access to this submission");
        }

        return submission;
    }

    /**
     * Map a verification entity to a DTO.
     *
     * @param verification The verification entity
     * @return The verification DTO
     */
    private VerificationDTO mapVerificationToDto(Verification verification) {
        VerificationDTO dto = VerificationDTO.builder()
                .id(verification.getId())
                .submissionId(verification.getSubmissionId())
                .status(verification.getStatus().toString())
                .progress(verification.getProgress())
                .completedSteps(verification.getCompletedSteps())
                .currentStep(verification.getCurrentStep())
                .estimatedCompletionTime(verification.getEstimatedCompletionTime())
                .startedAt(verification.getStartedAt())
                .completedAt(verification.getCompletedAt())
                .build();

        // Add error information if available
        if (verification.getStatus() == Verification.VerificationStatus.FAILED &&
                verification.getErrorCode() != null) {

            VerificationDTO.ErrorInfo errorInfo = VerificationDTO.ErrorInfo.builder()
                    .code(verification.getErrorCode())
                    .message(verification.getErrorMessage())
                    .build();

            // Add error details if available
            if (verification.getErrorDetails() != null) {
                List<VerificationDTO.ErrorDetail> details = new ArrayList<>();

                // For simplicity, we're assuming error details are in "issue: suggestion" format
                String[] lines = verification.getErrorDetails().split("\\n");
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        details.add(VerificationDTO.ErrorDetail.builder()
                                .issue(parts[0].trim())
                                .suggestion(parts[1].trim())
                                .build());
                    }
                }

                // Set the details
                if (!details.isEmpty()) {
                    errorInfo.setDetails(details);
                }
            }

            dto.setError(errorInfo);
        }

        return dto;
    }

    /**
     * Map a compliance report entity to a DTO.
     *
     * @param report The compliance report entity
     * @return The compliance report DTO
     */
    private ComplianceReportDTO mapReportToDto(ComplianceReport report) {
        // Map critical issues
        List<ComplianceReportDTO.CriticalIssueDTO> criticalIssues = report.getCriticalIssues().stream()
                .map(issue -> ComplianceReportDTO.CriticalIssueDTO.builder()
                        .component(issue.getComponent())
                        .description(issue.getDescription())
                        .recommendation(issue.getRecommendation())
                        .build())
                .collect(Collectors.toList());

        // Map recommendations
        List<ComplianceReportDTO.RecommendationDTO> recommendations = report.getRecommendations().stream()
                .map(rec -> ComplianceReportDTO.RecommendationDTO.builder()
                        .component(rec.getComponent())
                        .priority(rec.getPriority().toString())
                        .description(rec.getDescription())
                        .action(rec.getAction())
                        .build())
                .collect(Collectors.toList());

        // Build component scores
        ComplianceReportDTO.ComponentScoresDTO componentScores = ComplianceReportDTO.ComponentScoresDTO.builder()
                .useCase(report.getUseCaseScore())
                .messages(report.getMessagesScore())
                .images(report.getImagesScore())
                .website(report.getWebsiteScore())
                .documents(report.getDocumentsScore())
                .build();

        return ComplianceReportDTO.builder()
                .submissionId(report.getSubmissionId())
                .overallScore(report.getOverallScore())
                .approvalLikelihood(report.getApprovalLikelihood().toString())
                .componentScores(componentScores)
                .criticalIssues(criticalIssues)
                .recommendations(recommendations)
                .generatedAt(report.getGeneratedAt())
                .build();
    }
}
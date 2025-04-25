package com.salesmsg.compliance.workflow;

import com.salesmsg.compliance.model.ComplianceReport;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.Verification;
import com.salesmsg.compliance.repository.ComplianceReportRepository;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.VerificationRepository;
import com.salesmsg.compliance.service.KendraService;
import com.salesmsg.compliance.workflow.nodes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VerificationOrchestratorIntegrationTest {

    private VerificationOrchestrator verificationOrchestrator;

    @Mock
    private ComplianceSubmissionRepository submissionRepository;

    @Mock
    private VerificationRepository verificationRepository;

    @Mock
    private ComplianceReportRepository reportRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private KendraService kendraService;

    @Mock
    private UseCaseVerificationAgent useCaseAgent;

    @Mock
    private MessageVerificationAgent messageAgent;

    @Mock
    private ImageVerificationAgent imageAgent;

    @Mock
    private WebsiteVerificationAgent websiteAgent;

    @Mock
    private DocumentVerificationAgent documentAgent;

    private ComplianceSubmission testSubmission;
    private Verification testVerification;
    private String testSubmissionId = "test-submission-123";
    private String testVerificationId = "test-verification-456";

    @BeforeEach
    void setUp() {
        // Create the verification orchestrator
        verificationOrchestrator = spy(new VerificationOrchestrator(
                chatClient,
                submissionRepository,
                verificationRepository,
                reportRepository,
                kendraService,
                useCaseAgent,
                messageAgent,
                imageAgent,
                websiteAgent,
                documentAgent));

        // Create a test submission
        testSubmission = new ComplianceSubmission();
        testSubmission.setId(testSubmissionId);
        testSubmission.setBusinessName("Test Business");
        testSubmission.setBusinessType("Retail");
        testSubmission.setUseCase("Order confirmations and shipping updates");
        testSubmission.setWebsiteUrl("https://example.com");
        testSubmission.setOptInMethod("webform");
        testSubmission.setStatus(ComplianceSubmission.SubmissionStatus.SUBMITTED);

        // Create a test verification
        testVerification = Verification.builder()
                .id(testVerificationId)
                .submissionId(testSubmissionId)
                .status(Verification.VerificationStatus.PENDING)
                .progress(0)
                .completedSteps(new ArrayList<>())
                .currentStep("planning")
                .estimatedCompletionTime(LocalDateTime.now().plusMinutes(15))
                .build();

        // CRITICAL: Disable the asynchronous execution to keep test simple
        doNothing().when(verificationOrchestrator).executeVerificationWorkflow(any(), any());
    }

    @Test
    void testStartVerification() {
        // Configure repository mocks for this test only
        when(submissionRepository.findById(testSubmissionId)).thenReturn(Optional.of(testSubmission));
        when(verificationRepository.save(any(Verification.class))).thenReturn(testVerification);

        // Call the method under test
        Verification result = verificationOrchestrator.startVerification(testSubmissionId);

        // Verify the result
        assertNotNull(result);
        assertEquals(testVerificationId, result.getId());

        // Verify repository interactions - exactly once
        verify(verificationRepository, times(1)).save(any(Verification.class));

        // Verify submission status update
        ArgumentCaptor<ComplianceSubmission> submissionCaptor = ArgumentCaptor.forClass(ComplianceSubmission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        assertEquals(ComplianceSubmission.SubmissionStatus.VERIFYING, submissionCaptor.getValue().getStatus());
        assertEquals(testVerificationId, submissionCaptor.getValue().getVerificationId());

        // Verify the async workflow was started but not actually executed
        verify(verificationOrchestrator).executeVerificationWorkflow(any(Verification.class), any(ComplianceSubmission.class));
    }

    @Test
    void testProcessWorkflowResult() {
        // Configure repository mocks for this test only
        when(verificationRepository.findById(testVerificationId)).thenReturn(Optional.of(testVerification));
        when(submissionRepository.findById(testSubmissionId)).thenReturn(Optional.of(testSubmission));
        when(verificationRepository.save(any(Verification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.save(any(ComplianceSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.save(any(ComplianceReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Prepare mock result
        Map<String, Object> mockResult = new HashMap<>();
        Map<String, Object> componentScores = new HashMap<>();
        componentScores.put("use_case", 85.0f);
        componentScores.put("messages", 90.0f);
        componentScores.put("website", 75.0f);
        mockResult.put("component_scores", componentScores);
        mockResult.put("critical_issues", new ArrayList<>());
        mockResult.put("recommendations", new ArrayList<>());

        // Call the method directly
        verificationOrchestrator.processWorkflowResult(testVerificationId, mockResult);

        // Verify report creation
        verify(reportRepository).save(any(ComplianceReport.class));

        // Verify submission update
        verify(submissionRepository).save(argThat(s ->
                s.getStatus() == ComplianceSubmission.SubmissionStatus.VERIFIED &&
                        s.getComplianceScore() != null));

        // Verify verification completion
        verify(verificationRepository).save(argThat(v ->
                v.getStatus() == Verification.VerificationStatus.COMPLETED &&
                        v.getProgress() == 100 &&
                        v.getCompletedAt() != null));
    }
}
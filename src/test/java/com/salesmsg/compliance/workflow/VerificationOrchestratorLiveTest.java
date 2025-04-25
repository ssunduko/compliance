package com.salesmsg.compliance.workflow;

import com.salesmsg.compliance.ComplianceApplication;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.SubmissionMessage;
import com.salesmsg.compliance.model.Verification;
import com.salesmsg.compliance.repository.ComplianceReportRepository;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.SubmissionMessageRepository;
import com.salesmsg.compliance.repository.VerificationRepository;
import com.salesmsg.compliance.service.KendraService;
import com.salesmsg.compliance.workflow.nodes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ComplianceApplication.class)
@ActiveProfiles("test")
public class VerificationOrchestratorLiveTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ComplianceSubmissionRepository submissionRepository;

    @Mock
    private VerificationRepository verificationRepository;

    @Mock
    private ComplianceReportRepository reportRepository;

    @Mock
    private SubmissionMessageRepository messageRepository;

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

    @InjectMocks
    private VerificationOrchestrator verificationOrchestrator;

    private ComplianceSubmission testSubmission;
    private Verification testVerification;
    private String testSubmissionId = "live-test-submission-123";
    private String testVerificationId = "live-test-verification-456";

    @BeforeEach
    void setUp() {
        // Create a test submission with real-world data
        testSubmission = new ComplianceSubmission();
        testSubmission.setId(testSubmissionId);
        testSubmission.setBusinessName("Acme E-commerce Store");
        testSubmission.setBusinessType("Retail");
        testSubmission.setUseCase("Our company sends order confirmations, shipping updates, and delivery notifications to customers who have opted in during checkout. Messages include order details, tracking information, and estimated delivery times. We also send occasional promotional messages about sales and new products, but only to customers who have explicitly consented to marketing messages. All messages include clear opt-out instructions.");
        testSubmission.setWebsiteUrl("https://acme-ecommerce.example.com");
        testSubmission.setOptInMethod("webform");
        testSubmission.setStatus(ComplianceSubmission.SubmissionStatus.SUBMITTED);
        testSubmission.setUserId("test-user");
        testSubmission.setCompanyId("test-company");

        // Add sample messages to the submission
        List<SubmissionMessage> messages = new ArrayList<>();
        messages.add(createSampleMessage(
                "Your Acme order #12345 has been confirmed! Your items will ship within 2 business days. Tracking info will be sent when available. Reply STOP to opt out.",
                testSubmission));
        messages.add(createSampleMessage(
                "Acme: Your order has shipped! Track at: {{tracking_url}} Est. delivery: {{delivery_date}}. Questions? Call 555-123-4567. Reply STOP to unsubscribe.",
                testSubmission));

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

        // Configure mocks for repositories
        when(submissionRepository.findById(eq(testSubmissionId))).thenReturn(Optional.of(testSubmission));
        // IMPORTANT: Fix the verification repository mock
        when(verificationRepository.findById(eq(testVerificationId))).thenReturn(Optional.of(testVerification));
        when(messageRepository.findBySubmissionId(eq(testSubmissionId))).thenReturn(messages);

        // Mock Kendra service to return guidelines
        when(kendraService.retrieveCarrierGuidelines(anyString())).thenReturn(
                "Guidelines for Retail:\n\n" +
                        "1. Include business identification in all messages\n" +
                        "2. Include STOP opt-out instructions\n" +
                        "3. Keep messages under 160 characters when possible\n" +
                        "4. Don't use ALL CAPS or excessive punctuation\n" +
                        "5. Be clear about message frequency\n" +
                        "6. Don't send messages during odd hours\n" +
                        "7. Don't include prohibited content\n"
        );

    }

    private SubmissionMessage createSampleMessage(String text, ComplianceSubmission submission) {
        return SubmissionMessage.builder()
                .id(UUID.randomUUID().toString())
                .submission(submission)
                .messageText(text)
                .build();
    }

    @Test
    void testSimpleWorkflowProcessing() {
        // Set up a simple state
        Map<String, Object> state = new HashMap<>();
        state.put("verification_id", testVerificationId);
        state.put("submission_id", testSubmissionId);
        state.put("business_name", testSubmission.getBusinessName());
        state.put("business_type", testSubmission.getBusinessType());
        state.put("use_case", testSubmission.getUseCase());

        // Mock responses for workflow components
        Map<String, Object> componentScores = new HashMap<>();
        componentScores.put("use_case", 85.0f);
        componentScores.put("messages", 90.0f);

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("component_scores", componentScores);
        mockResult.put("critical_issues", new ArrayList<>());
        mockResult.put("recommendations", new ArrayList<>());

        // Set up mocks
        when(verificationRepository.save(any(Verification.class))).thenReturn(testVerification);
        when(submissionRepository.save(any(ComplianceSubmission.class))).thenReturn(testSubmission);

        // CRUCIAL: Make sure the repository returns the verification when queried
        verify(verificationRepository, never()).findById(testVerificationId);
        when(verificationRepository.findById(eq(testVerificationId))).thenReturn(Optional.of(testVerification));

        // Test processWorkflowResult method
        verificationOrchestrator.processWorkflowResult(testVerificationId, mockResult);

        // Verify that the verification was marked as completed
        verify(verificationRepository).save(argThat(v ->
                v.getStatus() == Verification.VerificationStatus.COMPLETED &&
                        v.getProgress() == 100));

        // Verify that the submission was updated
        verify(submissionRepository).save(argThat(s ->
                s.getStatus() == ComplianceSubmission.SubmissionStatus.VERIFIED));

        // Verify that a report was created
        verify(reportRepository).save(any());
    }
}
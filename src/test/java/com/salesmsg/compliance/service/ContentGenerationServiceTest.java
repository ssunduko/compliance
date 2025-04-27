package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.MessageGenerationDTO;
import com.salesmsg.compliance.dto.UseCaseGenerationDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ContentGenerationServiceTest {

    @Autowired
    private ContentGenerationService contentGenerationService;

    @Test
    void testGenerateSampleMessages() {
        // Create request
        MessageGenerationDTO.Request request = MessageGenerationDTO.Request.builder()
                .businessType("Healthcare")
                .useCase("Appointment Reminders")
                .includeLinks(true)
                .includePhoneNumbers(false)
                .messageCount(2)
                .build();

        // Execute
        MessageGenerationDTO.Response response = contentGenerationService.generateSampleMessages(request);

        // Verify
        assertNotNull(response);
        assertFalse(response.getMessages().isEmpty());
        assertEquals(request.getMessageCount(), response.getMessages().size());

        // Verify each message contains appropriate elements
        for (String message : response.getMessages()) {
            assertTrue(message.length() > 0);
            // Check if messages contain opt-out instructions
            assertTrue(message.contains("STOP") || message.contains("stop") ||
                            message.contains("opt out") || message.contains("unsubscribe"),
                    "Message should contain opt-out instructions: " + message);

            // Check if messages have placeholders
            assertTrue(message.contains("{{") && message.contains("}}"),
                    "Message should contain placeholders: " + message);
        }

        // Verify metadata if available
        if (response.getMessageMetadata() != null && !response.getMessageMetadata().isEmpty()) {
            assertEquals(response.getMessages().size(), response.getMessageMetadata().size());

            for (MessageGenerationDTO.Response.MessageMetadata metadata : response.getMessageMetadata()) {
                assertTrue(metadata.getCharacterCount() > 0);
                assertTrue(metadata.getSegmentCount() > 0);
                // Opt-out and business identifier should generally be true for compliant messages
                assertTrue(metadata.getHasOptOut(), "Messages should have opt-out instructions");
                assertTrue(metadata.getHasBusinessIdentifier(), "Messages should have business identifiers");
            }
        }
    }

    @Test
    void testGenerateUseCase() {
        // Create request
        UseCaseGenerationDTO.Request request = UseCaseGenerationDTO.Request.builder()
                .businessType("Healthcare")
                .messagingPurpose("Appointment Reminders")
                .audienceType("Patients")
                .optInMethod("Intake Form")
                .build();

        // Execute
        UseCaseGenerationDTO.Response response = contentGenerationService.generateUseCase(request);

        // Verify
        assertNotNull(response);
        assertNotNull(response.getUseCase());

        // Verify use case contains key elements
        assertTrue(response.getUseCase().contains(request.getBusinessType()) ||
                        response.getUseCase().toLowerCase().contains(request.getBusinessType().toLowerCase()),
                "Use case should mention the business type");

        assertTrue(response.getUseCase().contains("opt") ||
                        response.getUseCase().contains("consent") ||
                        response.getUseCase().contains("permission"),
                "Use case should mention opt-in/consent");

        assertTrue(response.getUseCase().contains("STOP") ||
                        response.getUseCase().contains("stop") ||
                        response.getUseCase().contains("opt out") ||
                        response.getUseCase().contains("unsubscribe"),
                "Use case should mention opt-out method");

        // Verify compliance check if available
        if (response.getComplianceCheck() != null) {
            assertNotNull(response.getComplianceCheck().getIsCompliant());
            assertNotNull(response.getComplianceCheck().getComplianceScore());
            assertTrue(response.getComplianceCheck().getComplianceScore() >= 0 &&
                            response.getComplianceCheck().getComplianceScore() <= 100,
                    "Compliance score should be between 0 and 100");
            assertNotNull(response.getComplianceCheck().getFeedback());
        }
    }

    @Test
    void testGenerateSampleMessagesWithInvalidParams() {
        // Create request with minimal params
        MessageGenerationDTO.Request request = MessageGenerationDTO.Request.builder()
                .businessType("Retail")
                .useCase("Order Updates")
                .build();

        // Execute
        MessageGenerationDTO.Response response = contentGenerationService.generateSampleMessages(request);

        // Verify service handles null values properly
        assertNotNull(response);
        assertFalse(response.getMessages().isEmpty());
    }

    @Test
    void testGenerateUseCaseWithMinimalParams() {
        // Create request with minimal params
        UseCaseGenerationDTO.Request request = UseCaseGenerationDTO.Request.builder()
                .businessType("Retail")
                .messagingPurpose("Order Updates")
                .build();

        // Execute
        UseCaseGenerationDTO.Response response = contentGenerationService.generateUseCase(request);

        // Verify service handles null values properly
        assertNotNull(response);
        assertNotNull(response.getUseCase());
    }
}
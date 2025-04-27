package com.salesmsg.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.MessageValidationDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MessageValidationService.
 * Tests the validation of SMS messages against 10DLC compliance requirements.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MessageValidationServiceTest {

    @Autowired
    private MessageValidationService messageValidationService;

    /**
     * Test validation of compliant messages.
     */
    @Test
    void testValidateCompliantMessages() {
        // Create a list of sample compliant messages
        List<String> messages = Arrays.asList(
                "MedClinic: Your appointment with Dr. Smith is scheduled for Monday, July 10 at 2 PM. Reply HELP for help or STOP to cancel messages.",
                "MedClinic: Reminder - your appointment is tomorrow at 2 PM. Reply C to confirm or R to reschedule. Reply STOP to opt out."
        );

        // Create the validation request
        MessageValidationDTO request = MessageValidationDTO.builder()
                .messages(messages)
                .useCase("Healthcare Appointment Reminders")
                .shouldIncludePhoneNumber(false)
                .shouldIncludeLinks(false)
                .build();

        // Execute the validation
        MessageValidationDTO result = messageValidationService.validateMessages(request);

        // Verify the results
        assertNotNull(result);
        assertNotNull(result.getOverallCompliance());
        assertNotNull(result.getComplianceScore());
        assertTrue(result.getComplianceScore() >= 80.0f, "Compliant messages should receive a high score");

        // Verify individual message results
        assertNotNull(result.getMessageResults());
        assertEquals(2, result.getMessageResults().size());

        // First message should be compliant
        assertTrue(result.getMessageResults().get(0).getCompliant(),
                "First message should be compliant");
        assertTrue(result.getMessageResults().get(0).getHasRequiredElements(),
                "First message should have required elements");

        // Second message should be compliant
        assertTrue(result.getMessageResults().get(1).getCompliant(),
                "Second message should be compliant");
        assertTrue(result.getMessageResults().get(1).getHasRequiredElements(),
                "Second message should have required elements");
    }

    /**
     * Test validation of non-compliant messages.
     */
    @Test
    void testValidateNonCompliantMessages() {
        // Create a list of sample non-compliant messages
        List<String> messages = Arrays.asList(
                "URGENT! Your account needs immediate attention! Click here: http://example.com to verify NOW!!!!",
                "Win a FREE iPhone! Just respond YES to this msg & we'll pick winners next week! Limited time offer!"
        );

        // Create the validation request
        MessageValidationDTO request = MessageValidationDTO.builder()
                .messages(messages)
                .useCase("Account Security Alerts")
                .shouldIncludePhoneNumber(false)
                .shouldIncludeLinks(true)
                .build();

        // Execute the validation
        MessageValidationDTO result = messageValidationService.validateMessages(request);

        // Verify the results
        assertNotNull(result);
        assertNotNull(result.getOverallCompliance());
        assertFalse(result.getOverallCompliance(), "Messages should not be compliant overall");
        assertNotNull(result.getComplianceScore());
        assertTrue(result.getComplianceScore() < 70.0f, "Non-compliant messages should receive a low score");

        // Verify individual message results
        assertNotNull(result.getMessageResults());
        assertEquals(2, result.getMessageResults().size());

        // Messages should have issues
        assertFalse(result.getMessageResults().get(0).getCompliant(),
                "First message should not be compliant");
        assertFalse(result.getMessageResults().get(1).getCompliant(),
                "Second message should not be compliant");

        // Check for specific issues
        assertTrue(result.getMessageResults().get(0).getIssues() != null &&
                        !result.getMessageResults().get(0).getIssues().isEmpty(),
                "First message should have compliance issues");
        assertTrue(result.getMessageResults().get(1).getIssues() != null &&
                        !result.getMessageResults().get(1).getIssues().isEmpty(),
                "Second message should have compliance issues");

        // Verify suggested revisions are provided
        assertNotNull(result.getMessageResults().get(0).getSuggestedRevision(),
                "First message should have a suggested revision");
        assertNotNull(result.getMessageResults().get(1).getSuggestedRevision(),
                "Second message should have a suggested revision");
    }

    /**
     * Test validation of messages with mixed compliance.
     */
    @Test
    void testValidateMixedMessages() {
        // Create a list with both compliant and non-compliant messages
        List<String> messages = Arrays.asList(
                "BookStore: Your order #1234 has shipped and will arrive on June 2. Track at {{tracking_url}}. Reply STOP to opt out.",
                "LAST CHANCE!!! 75% OFF EVERYTHING!!! BUY NOW BEFORE IT'S ALL GONE!!!"
        );

        // Create the validation request
        MessageValidationDTO request = MessageValidationDTO.builder()
                .messages(messages)
                .useCase("Order Updates")
                .shouldIncludePhoneNumber(false)
                .shouldIncludeLinks(true)
                .build();

        // Execute the validation
        MessageValidationDTO result = messageValidationService.validateMessages(request);

        // Verify the results
        assertNotNull(result);
        assertNotNull(result.getOverallCompliance());
        assertFalse(result.getOverallCompliance(), "Mixed messages should not be compliant overall");

        // Verify individual message results
        assertNotNull(result.getMessageResults());
        assertEquals(2, result.getMessageResults().size());

        // First message should be compliant
        assertTrue(result.getMessageResults().get(0).getCompliant(),
                "First message should be compliant");

        // Second message should not be compliant
        assertFalse(result.getMessageResults().get(1).getCompliant(),
                "Second message should not be compliant");
    }

    /**
     * Test validation of a message with missing required elements.
     */
    @Test
    void testValidateMessagesWithMissingElements() {
        // Create a message missing required elements (opt-out instructions)
        List<String> messages = Arrays.asList(
                "RetailCo: Thank you for your purchase! Your order #5678 has been confirmed and will ship within 2 business days."
        );

        // Create the validation request
        MessageValidationDTO request = MessageValidationDTO.builder()
                .messages(messages)
                .useCase("Order Confirmations")
                .shouldIncludePhoneNumber(false)
                .shouldIncludeLinks(false)
                .build();

        // Execute the validation
        MessageValidationDTO result = messageValidationService.validateMessages(request);

        // Verify the results
        assertNotNull(result);
        assertNotNull(result.getMessageResults());
        assertEquals(1, result.getMessageResults().size());

        // Message should be flagged as missing elements
        MessageValidationDTO.MessageResultDTO messageResult = result.getMessageResults().get(0);

        // Verify that a suggested revision is provided
        assertNotNull(messageResult.getSuggestedRevision(),
                "A suggested revision should be provided");
        assertTrue(messageResult.getSuggestedRevision().contains("STOP"),
                "Suggested revision should include the missing opt-out instructions");
    }

    /**
     * Test validation with use case mismatch.
     */
    @Test
    void testValidateMessagesWithUseCaseMismatch() {
        // Create a message that doesn't match the stated use case
        List<String> messages = Arrays.asList(
                "FlightCo: Enjoy 50% off all international flights until midnight tonight! Book at flightco.example.com. Reply STOP to opt out."
        );

        // Create the validation request
        MessageValidationDTO request = MessageValidationDTO.builder()
                .messages(messages)
                .useCase("Flight Status Updates")  // This use case doesn't match a promotional message
                .shouldIncludePhoneNumber(false)
                .shouldIncludeLinks(true)
                .build();

        // Execute the validation
        MessageValidationDTO result = messageValidationService.validateMessages(request);

        // Verify the results
        assertNotNull(result);
        assertNotNull(result.getMessageResults());
        assertEquals(1, result.getMessageResults().size());

        // Message should be flagged as not matching the use case
        MessageValidationDTO.MessageResultDTO messageResult = result.getMessageResults().get(0);
        assertFalse(messageResult.getMatchesUseCase(),
                "Message should be flagged as not matching the use case");

        // Verify that a suggested revision is provided
        assertNotNull(messageResult.getSuggestedRevision(),
                "A suggested revision should be provided");
    }

    /**
     * Test validating a large number of messages.
     */
    @Test
    void testValidateManyMessages() {
        // Create a list with multiple messages
        List<String> messages = Arrays.asList(
                "Store1: Your order is confirmed. Reply STOP to opt out.",
                "Store2: Your order has shipped. Track at {{url}}. Reply STOP to opt out.",
                "Store3: Delivery scheduled for tomorrow. Reply STOP to opt out.",
                "Store4: Order delivered. Reply STOP to opt out.",
                "Store5: Thanks for your purchase! Reply STOP to opt out."
        );

        // Create the validation request
        MessageValidationDTO request = MessageValidationDTO.builder()
                .messages(messages)
                .useCase("Order Updates")
                .shouldIncludePhoneNumber(false)
                .shouldIncludeLinks(true)
                .build();

        // Execute the validation
        MessageValidationDTO result = messageValidationService.validateMessages(request);

        // Verify the results
        assertNotNull(result);
        assertNotNull(result.getMessageResults());
        assertEquals(5, result.getMessageResults().size(),
                "All messages should be validated");
    }
}
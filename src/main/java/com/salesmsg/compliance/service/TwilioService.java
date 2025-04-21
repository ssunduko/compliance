package com.salesmsg.compliance.service;

import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.SubmissionMessage;
import com.salesmsg.compliance.repository.SubmissionMessageRepository;
import com.twilio.Twilio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for interacting with Twilio's API for 10DLC campaign registrations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioService {

    private final SubmissionMessageRepository messageRepository;

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.messaging.service.sid}")
    private String messagingServiceSid;


    /**
     * Submit a 10DLC campaign to Twilio.
     *
     * @param submission The compliance submission
     * @return The Twilio campaign ID
     */
    public String submitTenDlcCampaign(ComplianceSubmission submission) {
        try {
            log.info("Submitting 10DLC campaign to Twilio for submission: {}", submission.getId());

            // Get the sample messages
            List<SubmissionMessage> messages = messageRepository.findBySubmissionId(submission.getId());
            List<String> messageTexts = messages.stream()
                    .map(SubmissionMessage::getMessageText)
                    .collect(Collectors.toList());

            // Map business type to Twilio's use case
            return mapBusinessTypeToUseCaseId(submission.getBusinessType());

        } catch (Exception e) {
            log.error("Error submitting 10DLC campaign to Twilio", e);
            throw new RuntimeException("Failed to submit 10DLC campaign to Twilio: " + e.getMessage(), e);
        }
    }

    /**
     * Map our business type to Twilio's use case ID.
     *
     * @param businessType Our business type
     * @return Twilio's use case ID
     */
    private String mapBusinessTypeToUseCaseId(String businessType) {
        return null;
    }

    /**
     * Map our opt-in method to Twilio's format.
     *
     * @param optInMethod Our opt-in method
     * @return Twilio's opt-in type
     */
    private String mapOptInMethod(String optInMethod) {
        // In a real implementation, this would map to Twilio's opt-in types
        // For demonstration purposes, we'll use placeholders
        if (optInMethod == null) {
            return "WEBSITE";
        }

        switch (optInMethod.toLowerCase()) {
            case "webform":
                return "WEBSITE";
            case "paper_form":
                return "PAPER";
            case "verbal":
                return "VERBAL";
            case "mobile_app":
                return "MOBILE_APP";
            default:
                return "WEBSITE";
        }
    }
}
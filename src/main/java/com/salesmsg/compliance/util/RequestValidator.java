package com.salesmsg.compliance.util;

import com.salesmsg.compliance.dto.MessageGenerationDTO;
import com.salesmsg.compliance.dto.UseCaseGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.validation.ValidationException;

/**
 * Utility class for validating request objects before processing.
 * Provides additional validation beyond the standard @Valid annotation.
 */
@Component
@Slf4j
public class RequestValidator {

    /**
     * Validate a message generation request.
     *
     * @param request The message generation request to validate
     * @throws ValidationException if the request is invalid
     */
    public void validateMessageGenerationRequest(MessageGenerationDTO.Request request) {
        if (request == null) {
            throw new ValidationException("Request cannot be null");
        }

        if (request.getUseCase() == null || request.getUseCase().trim().isEmpty()) {
            throw new ValidationException("Use case is required");
        }

        if (request.getBusinessType() == null || request.getBusinessType().trim().isEmpty()) {
            throw new ValidationException("Business type is required");
        }

        // If messageCount is not specified, set a default value
        if (request.getMessageCount() == null) {
            request.setMessageCount(3); // Default to 3 messages
            log.debug("Message count not specified, defaulting to 3");
        } else if (request.getMessageCount() < 1 || request.getMessageCount() > 10) {
            throw new ValidationException("Message count must be between 1 and 10");
        }

        // If includeLinks or includePhoneNumbers are not specified, default to false
        if (request.getIncludeLinks() == null) {
            request.setIncludeLinks(false);
            log.debug("Include links not specified, defaulting to false");
        }

        if (request.getIncludePhoneNumbers() == null) {
            request.setIncludePhoneNumbers(false);
            log.debug("Include phone numbers not specified, defaulting to false");
        }
    }

    /**
     * Validate a use case generation request.
     *
     * @param request The use case generation request to validate
     * @throws ValidationException if the request is invalid
     */
    public void validateUseCaseGenerationRequest(UseCaseGenerationDTO.Request request) {
        if (request == null) {
            throw new ValidationException("Request cannot be null");
        }

        if (request.getBusinessType() == null || request.getBusinessType().trim().isEmpty()) {
            throw new ValidationException("Business type is required");
        }

        if (request.getMessagingPurpose() == null || request.getMessagingPurpose().trim().isEmpty()) {
            throw new ValidationException("Messaging purpose is required");
        }

        // If audienceType is not specified, set a default value
        if (request.getAudienceType() == null || request.getAudienceType().trim().isEmpty()) {
            request.setAudienceType("Customers");
            log.debug("Audience type not specified, defaulting to 'Customers'");
        }

        // If optInMethod is not specified, set a default value
        if (request.getOptInMethod() == null || request.getOptInMethod().trim().isEmpty()) {
            request.setOptInMethod("Website form");
            log.debug("Opt-in method not specified, defaulting to 'Website form'");
        }
    }
}
package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for validating SMS messages for 10DLC compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageValidationDTO {

    @NotEmpty(message = "Messages list cannot be empty")
    @Size(min = 1, message = "At least one message is required")
    private List<String> messages;

    @NotBlank(message = "Use case is required")
    private String useCase;

    private Boolean shouldIncludePhoneNumber;
    private Boolean shouldIncludeLinks;

    // Response fields
    private Boolean overallCompliance;
    private Float complianceScore;
    private List<MessageResultDTO> messageResults;
    private List<String> recommendations;

    /**
     * DTO for individual message validation results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResultDTO {
        private String text;
        private Boolean compliant;
        private Boolean matchesUseCase;
        private Boolean hasRequiredElements;
        private List<String> missingElements;
        private List<MessageIssueDTO> issues;
        private String suggestedRevision;
    }

    /**
     * DTO for issues found in messages.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageIssueDTO {
        private String severity;
        private String description;
    }
}
package com.salesmsg.compliance.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for creating or updating a 10DLC compliance submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceSubmissionDTO {

    private String id;

    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name must be 255 characters or less")
    private String businessName;

    @NotBlank(message = "Business type is required")
    private String businessType;

    @NotBlank(message = "Use case is required")
    private String useCase;

    private String optInMethod;

    private String optInMethodDescription;

    private String websiteUrl;

    private Boolean includesSubscriptionServices;

    private Boolean includesMarketing;

    private String status;

    private Float complianceScore;

    private String verificationId;

    private String carrierSubmissionId;

    private String carrierRejectionReason;

    @NotNull(message = "Sample messages are required")
    @Size(min = 1, message = "At least one sample message is required")
    private List<SampleMessageDTO> sampleMessages;

    private List<SubmissionImageDTO> images;

    private List<SubmissionDocumentDTO> documents;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * DTO for a sample message within a compliance submission.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleMessageDTO {
        private String id;

        @NotBlank(message = "Message text is required")
        private String messageText;

        private Boolean compliant;
        private Boolean matchesUseCase;
        private Boolean hasRequiredElements;
        private String missingElements;
        private String issues;
        private String suggestedRevision;
    }

    /**
     * DTO for an image within a compliance submission.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionImageDTO {
        private String id;
        private String imageUrl;
        private String imageType;
        private String fileName;
        private Long fileSize;
        private String optInType;
        private String description;
        private Boolean hasRequiredElements;
        private String detectedElements;
        private String missingElements;
        private String textQuality;
        private Float complianceScore;
        private String recommendations;
    }

    /**
     * DTO for a document within a compliance submission.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionDocumentDTO {
        private String id;
        private String documentUrl;
        private String documentType;
        private String fileName;
        private Long fileSize;
        private String description;
        private Boolean compliant;
        private Boolean hasRequiredElements;
        private String detectedElements;
        private String missingElements;
        private String contentIssues;
        private Float complianceScore;
        private String recommendations;
    }
}
package com.salesmsg.compliance.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for submission status information.
 * Used for providing status updates about 10DLC submissions, especially
 * during carrier submission and verification processes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionStatusDTO {

    private String submissionId;
    private String status;
    private Float complianceScore;
    private String verificationId;
    private String carrierSubmissionId;
    private String carrierStatus;
    private String rejectionReason;
    private LocalDateTime estimatedProcessingTime;
    private LocalDateTime submittedAt;
    private LocalDateTime lastUpdatedAt;

    /**
     * Optional error information if there was an issue with the submission.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private String code;
        private String message;
        private String details;
    }

    private ErrorInfo error;
}
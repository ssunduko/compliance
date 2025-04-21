package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for verification process information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationDTO {

    private String id;
    private String submissionId;
    private String status;
    private Integer progress;
    private List<String> completedSteps;
    private String currentStep;
    private LocalDateTime estimatedCompletionTime;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /**
     * Error information if verification failed.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private String code;
        private String message;
        private List<ErrorDetail> details;
    }

    /**
     * Detailed error information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String issue;
        private String suggestion;
    }
}
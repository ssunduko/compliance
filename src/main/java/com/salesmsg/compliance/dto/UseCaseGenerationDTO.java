package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for use case generation requests and responses.
 * Used for AI-powered generation of compliant use case descriptions.
 */
public class UseCaseGenerationDTO {

    /**
     * Request for generating a use case description.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Request {

        @NotBlank(message = "Business type is required")
        private String businessType;

        @NotBlank(message = "Messaging purpose is required")
        private String messagingPurpose;

        private String audienceType;

        private String optInMethod;
    }

    /**
     * Response containing a generated use case description.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {

        private String useCase;

        /**
         * Optional compliance check results for the generated use case.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComplianceCheck {
            private Boolean isCompliant;
            private Float complianceScore;
            private String feedback;
        }

        private ComplianceCheck complianceCheck;
    }
}
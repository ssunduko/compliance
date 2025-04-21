package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for message generation requests and responses.
 * Used for AI-powered generation of compliant sample messages.
 */
public class MessageGenerationDTO {

    /**
     * Request for generating sample messages.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Request {

        @NotBlank(message = "Use case is required")
        private String useCase;

        @NotBlank(message = "Business type is required")
        private String businessType;

        private Boolean includeLinks;

        private Boolean includePhoneNumbers;

        @Min(value = 1, message = "Message count must be at least 1")
        @Max(value = 10, message = "Message count cannot exceed 10")
        private Integer messageCount;
    }

    /**
     * Response containing generated sample messages.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {

        private List<String> messages;

        /**
         * Optional metadata about the generated messages.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MessageMetadata {
            private Integer characterCount;
            private Integer segmentCount;
            private Boolean hasOptOut;
            private Boolean hasBusinessIdentifier;
        }

        private List<MessageMetadata> messageMetadata;
    }
}
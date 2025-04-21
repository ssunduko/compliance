package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for website compliance check requests and responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebsiteCheckDTO {

    @NotBlank(message = "URL is required")
    private String url;

    private Boolean checkWebform;
    private String webformUrl;

    // Response fields
    private Boolean hasPrivacyPolicy;
    private String privacyPolicyUrl;
    private Boolean hasSmsDataSharingClause;
    private Boolean webformFunctional;
    private Boolean webformHasRequiredElements;
    private Float complianceScore;
    private List<WebsiteIssueDTO> issues;

    /**
     * DTO for issues found during website compliance check.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebsiteIssueDTO {
        private String severity;
        private String description;
        private String recommendation;
    }
}
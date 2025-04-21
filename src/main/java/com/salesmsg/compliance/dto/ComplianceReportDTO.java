package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for compliance report information.
 * Contains detailed verification results and recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceReportDTO {

    private String submissionId;
    private Float overallScore;
    private String approvalLikelihood;
    private ComponentScoresDTO componentScores;
    private List<CriticalIssueDTO> criticalIssues;
    private List<RecommendationDTO> recommendations;
    private LocalDateTime generatedAt;

    /**
     * Component-specific compliance scores.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentScoresDTO {
        private Float useCase;
        private Float messages;
        private Float images;
        private Float website;
        private Float documents;
    }

    /**
     * Details of a critical compliance issue.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriticalIssueDTO {
        private String component;
        private String description;
        private String recommendation;
    }

    /**
     * Recommendation for improving compliance.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationDTO {
        private String component;
        private String priority;
        private String description;
        private String action;
    }
}
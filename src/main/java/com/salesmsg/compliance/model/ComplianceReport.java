package com.salesmsg.compliance.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the compliance verification report for a 10DLC submission.
 * Contains detailed verification results, scores, issues, and recommendations.
 */
@Entity
@Table(name = "compliance_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "submission_id", nullable = false)
    private String submissionId;

    @Column(name = "overall_score", nullable = false)
    private Float overallScore;

    @Column(name = "approval_likelihood", nullable = false)
    @Enumerated(EnumType.STRING)
    private ApprovalLikelihood approvalLikelihood;

    @Column(name = "use_case_score")
    private Float useCaseScore;

    @Column(name = "messages_score")
    private Float messagesScore;

    @Column(name = "images_score")
    private Float imagesScore;

    @Column(name = "website_score")
    private Float websiteScore;

    @Column(name = "documents_score")
    private Float documentsScore;

    @ElementCollection
    @CollectionTable(name = "report_critical_issues",
            joinColumns = @JoinColumn(name = "report_id"))
    private List<CriticalIssue> criticalIssues = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "report_recommendations",
            joinColumns = @JoinColumn(name = "report_id"))
    private List<Recommendation> recommendations = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Enum representing the likelihood of carrier approval.
     */
    public enum ApprovalLikelihood {
        HIGH,     // Very likely to be approved
        MEDIUM,   // Might be approved with some changes
        LOW       // Unlikely to be approved without significant changes
    }

    /**
     * Embedded class representing a critical compliance issue.
     */
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CriticalIssue {
        @Column(name = "component")
        private String component;

        @Column(name = "description", columnDefinition = "TEXT")
        private String description;

        @Column(name = "recommendation", columnDefinition = "TEXT")
        private String recommendation;
    }

    /**
     * Embedded class representing a recommendation for improving compliance.
     */
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Recommendation {
        @Column(name = "component")
        private String component;

        @Column(name = "priority")
        @Enumerated(EnumType.STRING)
        private RecommendationPriority priority;

        @Column(name = "description", columnDefinition = "TEXT")
        private String description;

        @Column(name = "action", columnDefinition = "TEXT")
        private String action;
    }

    /**
     * Enum representing the priority of a recommendation.
     */
    public enum RecommendationPriority {
        HIGH,
        MEDIUM,
        LOW
    }
}
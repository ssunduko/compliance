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
 * Represents a website compliance check result.
 * Stores detailed verification of a website for SMS compliance requirements.
 */
@Entity
@Table(name = "website_checks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "has_privacy_policy")
    private Boolean hasPrivacyPolicy;

    @Column(name = "privacy_policy_url")
    private String privacyPolicyUrl;

    @Column(name = "has_sms_data_sharing_clause")
    private Boolean hasSmsDataSharingClause;

    @Column(name = "webform_functional")
    private Boolean webformFunctional;

    @Column(name = "webform_url")
    private String webformUrl;

    @Column(name = "webform_has_required_elements")
    private Boolean webformHasRequiredElements;

    @Column(name = "compliance_score")
    private Float complianceScore;

    @ElementCollection
    @CollectionTable(name = "website_check_issues",
            joinColumns = @JoinColumn(name = "website_check_id"))
    private List<WebsiteIssue> issues = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Embedded class representing an issue found during website compliance check.
     */
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WebsiteIssue {
        @Column(name = "severity")
        @Enumerated(EnumType.STRING)
        private IssueSeverity severity;

        @Column(name = "description", columnDefinition = "TEXT")
        private String description;

        @Column(name = "recommendation", columnDefinition = "TEXT")
        private String recommendation;
    }

    /**
     * Enum representing the severity of a website compliance issue.
     */
    public enum IssueSeverity {
        CRITICAL,
        MAJOR,
        MINOR
    }
}
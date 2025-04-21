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
 * Represents a 10DLC compliance submission for verification.
 * This is the main entity that contains all the information about a messaging campaign
 * that needs to be validated for carrier compliance.
 */
@Entity
@Table(name = "compliance_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(name = "use_case", nullable = false, columnDefinition = "TEXT")
    private String useCase;

    @Column(name = "opt_in_method")
    private String optInMethod;

    @Column(name = "opt_in_method_description", columnDefinition = "TEXT")
    private String optInMethodDescription;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "includes_subscription_services")
    private Boolean includesSubscriptionServices;

    @Column(name = "includes_marketing")
    private Boolean includesMarketing;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;

    @Column(name = "compliance_score")
    private Float complianceScore;

    @Column(name = "verification_id")
    private String verificationId;

    @Column(name = "carrier_submission_id")
    private String carrierSubmissionId;

    @Column(name = "carrier_rejection_reason", columnDefinition = "TEXT")
    private String carrierRejectionReason;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionMessage> sampleMessages = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Enum representing the possible states of a compliance submission.
     */
    public enum SubmissionStatus {
        DRAFT,         // Initial state, still being edited
        SUBMITTED,     // Submitted for verification
        VERIFYING,     // Verification in progress
        VERIFIED,      // Verification completed
        REJECTED,      // Rejected by carrier
        APPROVED       // Approved by carrier
    }
}
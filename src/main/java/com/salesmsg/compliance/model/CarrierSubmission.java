package com.salesmsg.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a submission to a carrier (like Twilio) for 10DLC campaign registration.
 * Tracks the status and response from the carrier.
 */
@Entity
@Table(name = "carrier_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "submission_id", nullable = false)
    private String submissionId;

    @Column(name = "carrier_id", nullable = false)
    private String carrierId;

    @Column(name = "carrier_submission_id")
    private String carrierSubmissionId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;

    @Column(name = "carrier_status")
    private String carrierStatus;

    @Column(name = "carrier_response", columnDefinition = "TEXT")
    private String carrierResponse;

    @Column(name = "has_privacy_policy")
    private Boolean hasPrivacyPolicy;

    @Column(name = "is_opt_in_compliant")
    private Boolean isOptInCompliant;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "recommended_actions", columnDefinition = "TEXT")
    private String recommendedActions;

    @Column(name = "estimated_processing_time")
    private LocalDateTime estimatedProcessingTime;

    @Column(name = "response_date")
    private LocalDateTime responseDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Enum representing the possible states of a carrier submission.
     */
    public enum SubmissionStatus {
        PENDING,     // Submission is waiting to be processed by carrier
        SUBMITTED,   // Submission has been sent to the carrier
        REVIEWING,   // Carrier is reviewing the submission
        APPROVED,    // Carrier has approved the submission
        REJECTED     // Carrier has rejected the submission
    }
}

package com.salesmsg.compliance.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a verification process for a 10DLC compliance submission.
 * Tracks the progress and results of the automated compliance verification.
 */
@Entity
@Table(name = "verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "submission_id", nullable = false)
    private String submissionId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private VerificationStatus status;

    @Column(name = "progress")
    private Integer progress;

    @ElementCollection
    @CollectionTable(name = "verification_completed_steps",
            joinColumns = @JoinColumn(name = "verification_id"))
    @Column(name = "step")
    private List<String> completedSteps;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "estimated_completion_time")
    private LocalDateTime estimatedCompletionTime;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Enum representing the possible states of a verification process.
     */
    public enum VerificationStatus {
        PENDING,    // Verification is waiting to start
        RUNNING,    // Verification is in progress
        COMPLETED,  // Verification completed successfully
        FAILED      // Verification failed
    }
}

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
 * Represents a sample message template submitted as part of a 10DLC compliance submission.
 * These are the actual message templates that will be sent to recipients.
 */
@Entity
@Table(name = "submission_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private ComplianceSubmission submission;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "compliant")
    private Boolean compliant;

    @Column(name = "matches_use_case")
    private Boolean matchesUseCase;

    @Column(name = "has_required_elements")
    private Boolean hasRequiredElements;

    @Column(name = "missing_elements", columnDefinition = "TEXT")
    private String missingElements;

    @Column(name = "issues", columnDefinition = "TEXT")
    private String issues;

    @Column(name = "suggested_revision", columnDefinition = "TEXT")
    private String suggestedRevision;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
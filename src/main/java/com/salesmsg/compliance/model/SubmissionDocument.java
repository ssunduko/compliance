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
 * Represents a document submitted as part of a 10DLC compliance submission.
 * These documents typically include privacy policies, terms of service, etc.
 */
@Entity
@Table(name = "submission_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private ComplianceSubmission submission;

    @Column(name = "document_url", nullable = false)
    private String documentUrl;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "compliant")
    private Boolean compliant;

    @Column(name = "has_required_elements")
    private Boolean hasRequiredElements;

    @Column(name = "detected_elements", columnDefinition = "TEXT")
    private String detectedElements;

    @Column(name = "missing_elements", columnDefinition = "TEXT")
    private String missingElements;

    @Column(name = "content_issues", columnDefinition = "TEXT")
    private String contentIssues;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "compliance_score")
    private Float complianceScore;

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
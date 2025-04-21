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
 * Represents an image submitted as part of a 10DLC compliance submission.
 * These images typically include screenshots of opt-in forms, terms, and conditions.
 */
@Entity
@Table(name = "submission_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private ComplianceSubmission submission;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "image_type")
    private String imageType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "opt_in_type")
    private String optInType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "has_required_elements")
    private Boolean hasRequiredElements;

    @Column(name = "detected_elements", columnDefinition = "TEXT")
    private String detectedElements;

    @Column(name = "missing_elements", columnDefinition = "TEXT")
    private String missingElements;

    @Column(name = "text_quality")
    private String textQuality;

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
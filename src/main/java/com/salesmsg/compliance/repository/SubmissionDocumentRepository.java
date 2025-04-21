package com.salesmsg.compliance.repository;

import com.salesmsg.compliance.model.SubmissionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing SubmissionDocument entities.
 */
@Repository
public interface SubmissionDocumentRepository extends JpaRepository<SubmissionDocument, String> {

    /**
     * Find all documents for a specific submission.
     *
     * @param submissionId The submission ID
     * @return List of documents
     */
    List<SubmissionDocument> findBySubmissionId(String submissionId);

    /**
     * Delete all documents for a specific submission.
     *
     * @param submissionId The submission ID
     */
    @Modifying
    @Query("DELETE FROM SubmissionDocument d WHERE d.submission.id = :submissionId")
    void deleteBySubmissionId(String submissionId);

    /**
     * Find all documents of a specific type for a submission.
     *
     * @param submissionId The submission ID
     * @param documentType The document type
     * @return List of documents
     */
    List<SubmissionDocument> findBySubmissionIdAndDocumentType(String submissionId, String documentType);

    /**
     * Find compliant documents for a submission.
     *
     * @param submissionId The submission ID
     * @param compliant The compliant flag
     * @return List of compliant documents
     */
    List<SubmissionDocument> findBySubmissionIdAndCompliant(String submissionId, Boolean compliant);
}
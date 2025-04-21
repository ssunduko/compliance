package com.salesmsg.compliance.repository;

import com.salesmsg.compliance.model.SubmissionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing SubmissionMessage entities.
 */
@Repository
public interface SubmissionMessageRepository extends JpaRepository<SubmissionMessage, String> {

    /**
     * Find all messages for a specific submission.
     *
     * @param submissionId The submission ID
     * @return List of messages
     */
    List<SubmissionMessage> findBySubmissionId(String submissionId);

    /**
     * Delete all messages for a specific submission.
     *
     * @param submissionId The submission ID
     */
    @Modifying
    @Query("DELETE FROM SubmissionMessage m WHERE m.submission.id = :submissionId")
    void deleteBySubmissionId(String submissionId);

    /**
     * Count messages for a specific submission.
     *
     * @param submissionId The submission ID
     * @return The number of messages
     */
    long countBySubmissionId(String submissionId);

    /**
     * Find all compliant messages for a specific submission.
     *
     * @param submissionId The submission ID
     * @param compliant The compliant flag
     * @return List of compliant messages
     */
    List<SubmissionMessage> findBySubmissionIdAndCompliant(String submissionId, Boolean compliant);
}
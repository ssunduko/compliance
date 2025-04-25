package com.salesmsg.compliance.repository;

import com.salesmsg.compliance.model.Verification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Verification entities.
 */
@Repository
public interface VerificationRepository extends JpaRepository<Verification, String> {

    /**
     * Find a verification by submission ID.
     *
     * @param submissionId The submission ID
     * @return The verification, if found
     */
    Optional<Verification> findBySubmissionId(String submissionId);

    /**
     * Find all verifications with a specific status.
     *
     * @param status The verification status
     * @return List of matching verifications
     */
    List<Verification> findByStatus(Verification.VerificationStatus status);

    /**
     * Find all verifications that are running and started before a given time.
     * Useful for identifying stalled verification processes.
     *
     * @param status The verification status (typically RUNNING)
     * @param startedBefore The cutoff time
     * @return List of potentially stalled verifications
     */
    List<Verification> findByStatusAndStartedAtBefore(
            Verification.VerificationStatus status, LocalDateTime startedBefore);

    /**
     * Delete all verifications for a specific submission.
     *
     * @param submissionId The submission ID
     */
    @Modifying
    @Query("DELETE FROM Verification v WHERE v.submissionId = :submissionId")
    void deleteBySubmissionId(String submissionId);

    /**
     * Find all verifications with a specific current step.
     *
     * @param currentStep The current verification step
     * @return List of matching verifications
     */
    List<Verification> findByCurrentStep(String currentStep);

    /**
     * Find all verifications with completion estimated before a given time.
     *
     * @param time The reference time
     * @return List of verifications expected to complete before the given time
     */
    List<Verification> findByEstimatedCompletionTimeBefore(LocalDateTime time);

    /**
     * Find all failed verifications with a specific error code.
     *
     * @param status The verification status (typically FAILED)
     * @param errorCode The error code
     * @return List of matching verifications
     */
    List<Verification> findByStatusAndErrorCode(Verification.VerificationStatus status, String errorCode);
}
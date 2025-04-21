package com.salesmsg.compliance.repository;

import com.salesmsg.compliance.model.CarrierSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing CarrierSubmission entities.
 */
@Repository
public interface CarrierSubmissionRepository extends JpaRepository<CarrierSubmission, String> {

    /**
     * Find carrier submissions for a specific compliance submission.
     *
     * @param submissionId The compliance submission ID
     * @return List of carrier submissions
     */
    List<CarrierSubmission> findBySubmissionId(String submissionId);

    /**
     * Find a carrier submission by its carrier-assigned ID.
     *
     * @param carrierSubmissionId The carrier submission ID
     * @return The carrier submission, if found
     */
    Optional<CarrierSubmission> findByCarrierSubmissionId(String carrierSubmissionId);

    /**
     * Delete all carrier submissions for a specific compliance submission.
     *
     * @param submissionId The compliance submission ID
     */
    @Modifying
    @Query("DELETE FROM CarrierSubmission c WHERE c.submissionId = :submissionId")
    void deleteBySubmissionId(String submissionId);

    /**
     * Find all carrier submissions with a specific status.
     *
     * @param status The submission status
     * @return List of carrier submissions
     */
    List<CarrierSubmission> findByStatus(CarrierSubmission.SubmissionStatus status);

    /**
     * Find all carrier submissions that are pending and older than a given time.
     *
     * @param status The submission status
     * @param olderThan The cutoff time
     * @return List of stale carrier submissions
     */
    List<CarrierSubmission> findByStatusAndCreatedAtBefore(
            CarrierSubmission.SubmissionStatus status, LocalDateTime olderThan);

    /**
     * Find the most recent carrier submission for a specific compliance submission.
     *
     * @param submissionId The compliance submission ID
     * @return The most recent carrier submission, if any
     */
    Optional<CarrierSubmission> findFirstBySubmissionIdOrderByCreatedAtDesc(String submissionId);
}
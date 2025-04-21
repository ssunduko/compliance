package com.salesmsg.compliance.repository;

import com.salesmsg.compliance.model.ComplianceSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing ComplianceSubmission entities.
 */
@Repository
public interface ComplianceSubmissionRepository extends JpaRepository<ComplianceSubmission, String> {

    /**
     * Find all submissions for a specific user.
     *
     * @param userId The user ID
     * @return List of submissions
     */
    List<ComplianceSubmission> findByUserId(String userId);

    /**
     * Find all submissions for a specific user with the given status.
     *
     * @param userId The user ID
     * @param status The submission status
     * @return List of submissions
     */
    List<ComplianceSubmission> findByUserIdAndStatus(String userId, ComplianceSubmission.SubmissionStatus status);

    /**
     * Find all submissions for a specific company.
     *
     * @param companyId The company ID
     * @return List of submissions
     */
    List<ComplianceSubmission> findByCompanyId(String companyId);

    /**
     * Find all submissions with a specific verification ID.
     *
     * @param verificationId The verification ID
     * @return List of submissions
     */
    List<ComplianceSubmission> findByVerificationId(String verificationId);

    /**
     * Find a submission by its carrier submission ID.
     *
     * @param carrierSubmissionId The carrier submission ID
     * @return The submission, if found
     */
    ComplianceSubmission findByCarrierSubmissionId(String carrierSubmissionId);
}
package com.salesmsg.compliance.repository;

import com.salesmsg.compliance.model.ComplianceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing ComplianceReport entities.
 */
@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, String> {

    /**
     * Find a compliance report for a specific submission.
     *
     * @param submissionId The submission ID
     * @return The compliance report, if found
     */
    Optional<ComplianceReport> findBySubmissionId(String submissionId);

    /**
     * Delete all compliance reports for a specific submission.
     *
     * @param submissionId The submission ID
     */
    void deleteBySubmissionId(String submissionId);

    /**
     * Find all compliance reports with a specific approval likelihood.
     *
     * @param approvalLikelihood The approval likelihood
     * @return List of matching compliance reports
     */
    List<ComplianceReport> findByApprovalLikelihood(ComplianceReport.ApprovalLikelihood approvalLikelihood);

    /**
     * Find all compliance reports with an overall score greater than or equal to the specified value.
     *
     * @param minScore The minimum score
     * @return List of matching compliance reports
     */
    List<ComplianceReport> findByOverallScoreGreaterThanEqual(Float minScore);

    /**
     * Find all compliance reports with an overall score less than the specified value.
     *
     * @param maxScore The maximum score
     * @return List of matching compliance reports
     */
    List<ComplianceReport> findByOverallScoreLessThan(Float maxScore);
}
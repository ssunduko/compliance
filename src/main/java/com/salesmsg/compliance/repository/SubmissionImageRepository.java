package com.salesmsg.compliance.repository;


import com.salesmsg.compliance.model.SubmissionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing SubmissionImage entities.
 */
@Repository
public interface SubmissionImageRepository extends JpaRepository<SubmissionImage, String> {

    /**
     * Find all images for a specific submission.
     *
     * @param submissionId The submission ID
     * @return List of images
     */
    List<SubmissionImage> findBySubmissionId(String submissionId);

    /**
     * Delete all images for a specific submission.
     *
     * @param submissionId The submission ID
     */
    @Modifying
    @Query("DELETE FROM SubmissionImage i WHERE i.submission.id = :submissionId")
    void deleteBySubmissionId(String submissionId);

    /**
     * Find all images of a specific type for a submission.
     *
     * @param submissionId The submission ID
     * @param imageType The image type
     * @return List of images
     */
    List<SubmissionImage> findBySubmissionIdAndImageType(String submissionId, String imageType);

    /**
     * Find all images with a specific opt-in type for a submission.
     *
     * @param submissionId The submission ID
     * @param optInType The opt-in type
     * @return List of images
     */
    List<SubmissionImage> findBySubmissionIdAndOptInType(String submissionId, String optInType);
}
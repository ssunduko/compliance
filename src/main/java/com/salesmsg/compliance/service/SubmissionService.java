package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.ComplianceSubmissionDTO;
import com.salesmsg.compliance.dto.SubmissionStatusDTO;
import com.salesmsg.compliance.exception.ResourceNotFoundException;
import com.salesmsg.compliance.model.CarrierSubmission;
import com.salesmsg.compliance.model.ComplianceSubmission;
import com.salesmsg.compliance.model.SubmissionDocument;
import com.salesmsg.compliance.model.SubmissionImage;
import com.salesmsg.compliance.model.SubmissionMessage;
import com.salesmsg.compliance.repository.CarrierSubmissionRepository;
import com.salesmsg.compliance.repository.ComplianceSubmissionRepository;
import com.salesmsg.compliance.repository.SubmissionDocumentRepository;
import com.salesmsg.compliance.repository.SubmissionImageRepository;
import com.salesmsg.compliance.repository.SubmissionMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing 10DLC compliance submissions.
 * Handles creation, updating, listing, and carrier submission operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final ComplianceSubmissionRepository submissionRepository;
    private final SubmissionMessageRepository messageRepository;
    private final SubmissionImageRepository imageRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final CarrierSubmissionRepository carrierSubmissionRepository;

    private final AWSService awsService;
    private final TwilioService twilioService;

    /**
     * Create a new compliance submission.
     *
     * @param submissionDTO The submission details
     * @param userId The user ID of the creator
     * @return The created submission
     */
    @Transactional
    public ComplianceSubmissionDTO createSubmission(ComplianceSubmissionDTO submissionDTO, String userId) {
        log.info("Creating new submission for user: {}, business: {}", userId, submissionDTO.getBusinessName());

        // Map DTO to entity
        ComplianceSubmission submission = mapDtoToEntity(submissionDTO, userId);
        submission.setStatus(ComplianceSubmission.SubmissionStatus.DRAFT);

        // Save the submission
        submission = submissionRepository.save(submission);
        String submissionId = submission.getId();

        // Create and save sample messages
        if (submissionDTO.getSampleMessages() != null && !submissionDTO.getSampleMessages().isEmpty()) {
            ComplianceSubmission finalSubmission = submission;
            List<SubmissionMessage> messages = submissionDTO.getSampleMessages().stream()
                    .map(messageDTO -> SubmissionMessage.builder()
                            .submission(finalSubmission)
                            .messageText(messageDTO.getMessageText())
                            .build())
                    .collect(Collectors.toList());

            messageRepository.saveAll(messages);
        }

        log.info("Created submission with ID: {}", submissionId);

        // Map the saved entity back to DTO
        return getSubmission(submissionId, userId);
    }

    /**
     * Get a submission by ID.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     * @return The submission details
     */
    @Transactional(readOnly = true)
    public ComplianceSubmissionDTO getSubmission(String submissionId, String userId) {
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        return mapEntityToDto(submission);
    }

    /**
     * Update an existing submission.
     *
     * @param submissionId The submission ID
     * @param submissionDTO The updated submission details
     * @param userId The user ID of the requester
     * @return The updated submission
     */
    @Transactional
    public ComplianceSubmissionDTO updateSubmission(String submissionId, ComplianceSubmissionDTO submissionDTO, String userId) {
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Check if the submission can be updated
        if (submission.getStatus() != ComplianceSubmission.SubmissionStatus.DRAFT &&
                submission.getStatus() != ComplianceSubmission.SubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("Submission cannot be updated in its current status: " + submission.getStatus());
        }

        // Update submission fields
        submission.setBusinessName(submissionDTO.getBusinessName());
        submission.setBusinessType(submissionDTO.getBusinessType());
        submission.setUseCase(submissionDTO.getUseCase());
        submission.setOptInMethod(submissionDTO.getOptInMethod());
        submission.setOptInMethodDescription(submissionDTO.getOptInMethodDescription());
        submission.setWebsiteUrl(submissionDTO.getWebsiteUrl());
        submission.setIncludesSubscriptionServices(submissionDTO.getIncludesSubscriptionServices());
        submission.setIncludesMarketing(submissionDTO.getIncludesMarketing());

        // Save the updated submission
        submission = submissionRepository.save(submission);

        // Update sample messages
        if (submissionDTO.getSampleMessages() != null) {
            // Delete existing messages
            messageRepository.deleteBySubmissionId(submissionId);

            // Add new messages
            ComplianceSubmission finalSubmission = submission;
            List<SubmissionMessage> messages = submissionDTO.getSampleMessages().stream()
                    .map(messageDTO -> SubmissionMessage.builder()
                            .submission(finalSubmission)
                            .messageText(messageDTO.getMessageText())
                            .build())
                    .collect(Collectors.toList());

            messageRepository.saveAll(messages);
        }

        log.info("Updated submission with ID: {}", submissionId);

        // Map the updated entity back to DTO
        return mapEntityToDto(submission);
    }

    /**
     * Delete a submission.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     */
    @Transactional
    public void deleteSubmission(String submissionId, String userId) {
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Check if the submission can be deleted
        if (submission.getStatus() == ComplianceSubmission.SubmissionStatus.VERIFYING ||
                submission.getStatus() == ComplianceSubmission.SubmissionStatus.APPROVED) {
            throw new IllegalStateException("Submission cannot be deleted in its current status: " + submission.getStatus());
        }

        // Delete related entities
        messageRepository.deleteBySubmissionId(submissionId);

        // Delete images from S3 and the database
        List<SubmissionImage> images = imageRepository.findBySubmissionId(submissionId);
        for (SubmissionImage image : images) {
            if (image.getS3Key() != null) {
                awsService.deleteFile(image.getS3Key());
            }
        }
        imageRepository.deleteBySubmissionId(submissionId);

        // Delete documents from S3 and the database
        List<SubmissionDocument> documents = documentRepository.findBySubmissionId(submissionId);
        for (SubmissionDocument document : documents) {
            if (document.getS3Key() != null) {
                awsService.deleteFile(document.getS3Key());
            }
        }
        documentRepository.deleteBySubmissionId(submissionId);

        // Delete carrier submissions
        carrierSubmissionRepository.deleteBySubmissionId(submissionId);

        // Delete the submission
        submissionRepository.deleteById(submissionId);

        log.info("Deleted submission with ID: {}", submissionId);
    }

    /**
     * List submissions with optional filtering.
     *
     * @param userId The user ID of the requester
     * @param status Optional status filter
     * @param page Page number (1-based)
     * @param limit Page size
     * @return List of submissions
     */
    @Transactional(readOnly = true)
    public List<ComplianceSubmissionDTO> listSubmissions(String userId, String status, int page, int limit) {
        List<ComplianceSubmission> submissions;

        // Apply filtering
        if (status != null && !status.isEmpty()) {
            try {
                ComplianceSubmission.SubmissionStatus statusEnum = ComplianceSubmission.SubmissionStatus.valueOf(status.toUpperCase());
                submissions = submissionRepository.findByUserIdAndStatus(userId, statusEnum);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                submissions = submissionRepository.findByUserId(userId);
            }
        } else {
            submissions = submissionRepository.findByUserId(userId);
        }

        // Apply pagination
        int offset = (page - 1) * limit;
        int toIndex = Math.min(offset + limit, submissions.size());

        if (offset < submissions.size()) {
            submissions = submissions.subList(offset, toIndex);
        } else {
            submissions = List.of();
        }

        // Map entities to DTOs
        return submissions.stream()
                .map(this::mapEntityToBasicDto)
                .collect(Collectors.toList());
    }

    /**
     * Add a document to a submission.
     *
     * @param submissionId The submission ID
     * @param file The document file
     * @param documentType The document type
     * @param description Optional description
     * @param userId The user ID of the requester
     * @return The uploaded document
     */
    @Transactional
    public ComplianceSubmissionDTO.SubmissionDocumentDTO addDocument(String submissionId, MultipartFile file,
                                                                     String documentType, String description, String userId) {
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Upload file to S3
        String fileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(fileName);
        String s3Key = "submissions/" + submissionId + "/documents/" + UUID.randomUUID() + fileExtension;
        String documentUrl = awsService.uploadFile(file, s3Key);

        // Create document entity
        SubmissionDocument document = SubmissionDocument.builder()
                .submission(submission)
                .documentUrl(documentUrl)
                .s3Key(s3Key)
                .documentType(documentType)
                .fileName(fileName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .description(description)
                .build();

        // Save the document
        document = documentRepository.save(document);

        log.info("Added document to submission {}: {}", submissionId, document.getId());

        // Map to DTO
        return ComplianceSubmissionDTO.SubmissionDocumentDTO.builder()
                .id(document.getId())
                .documentUrl(document.getDocumentUrl())
                .documentType(document.getDocumentType())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .description(document.getDescription())
                .build();
    }

    /**
     * Add an image to a submission.
     *
     * @param submissionId The submission ID
     * @param file The image file
     * @param imageType The image type
     * @param optInType The opt-in type
     * @param description Optional description
     * @param userId The user ID of the requester
     * @return The uploaded image
     */
    @Transactional
    public ComplianceSubmissionDTO.SubmissionImageDTO addImage(String submissionId, MultipartFile file,
                                                               String imageType, String optInType, String description, String userId) {
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Upload file to S3
        String fileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(fileName);
        String s3Key = "submissions/" + submissionId + "/images/" + UUID.randomUUID() + fileExtension;
        String imageUrl = awsService.uploadFile(file, s3Key);

        // Create image entity
        SubmissionImage image = SubmissionImage.builder()
                .submission(submission)
                .imageUrl(imageUrl)
                .s3Key(s3Key)
                .imageType(imageType)
                .optInType(optInType)
                .fileName(fileName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .description(description)
                .build();

        // Save the image
        image = imageRepository.save(image);

        log.info("Added image to submission {}: {}", submissionId, image.getId());

        // Map to DTO
        return ComplianceSubmissionDTO.SubmissionImageDTO.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .imageType(image.getImageType())
                .optInType(image.getOptInType())
                .fileName(image.getFileName())
                .fileSize(image.getFileSize())
                .description(image.getDescription())
                .build();
    }

    /**
     * Submit a verified submission to the carrier.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     * @return The submission status
     */
    @Transactional
    public SubmissionStatusDTO submitToCarrier(String submissionId, String userId) {
        ComplianceSubmission submission = getSubmissionWithAccessCheck(submissionId, userId);

        // Check if the submission is verified
        if (submission.getStatus() != ComplianceSubmission.SubmissionStatus.VERIFIED) {
            throw new IllegalStateException("Submission must be verified before submission to carrier");
        }

        // Submit to Twilio
        String carrierSubmissionId = twilioService.submitTenDlcCampaign(submission);

        // Create carrier submission record
        CarrierSubmission carrierSubmission = CarrierSubmission.builder()
                .submissionId(submissionId)
                .carrierId("twilio")
                .carrierSubmissionId(carrierSubmissionId)
                .status(CarrierSubmission.SubmissionStatus.SUBMITTED)
                .estimatedProcessingTime(LocalDateTime.now().plusDays(2))
                .build();

        carrierSubmissionRepository.save(carrierSubmission);

        // Update submission status
        submission.setStatus(ComplianceSubmission.SubmissionStatus.SUBMITTED);
        submission.setCarrierSubmissionId(carrierSubmissionId);
        submissionRepository.save(submission);

        log.info("Submitted to carrier: {}, carrierSubmissionId: {}", submissionId, carrierSubmissionId);

        return SubmissionStatusDTO.builder()
                .submissionId(submissionId)
                .status(submission.getStatus().toString())
                .carrierSubmissionId(carrierSubmissionId)
                .estimatedProcessingTime(carrierSubmission.getEstimatedProcessingTime())
                .build();
    }

    /**
     * Process a carrier webhook callback.
     *
     * @param carrierSubmissionId The carrier submission ID
     * @param status The new status
     * @param reason Optional rejection reason
     */
    @Transactional
    public void processCarrierCallback(String carrierSubmissionId, String status, String reason) {
        CarrierSubmission carrierSubmission = carrierSubmissionRepository.findByCarrierSubmissionId(carrierSubmissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier submission not found: " + carrierSubmissionId));

        String submissionId = carrierSubmission.getSubmissionId();
        ComplianceSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

        // Update carrier submission status
        carrierSubmission.setCarrierStatus(status);
        carrierSubmission.setResponseDate(LocalDateTime.now());

        // Handle approval or rejection
        if ("approved".equalsIgnoreCase(status)) {
            carrierSubmission.setStatus(CarrierSubmission.SubmissionStatus.APPROVED);
            submission.setStatus(ComplianceSubmission.SubmissionStatus.APPROVED);
        } else if ("rejected".equalsIgnoreCase(status)) {
            carrierSubmission.setStatus(CarrierSubmission.SubmissionStatus.REJECTED);
            carrierSubmission.setRejectionReason(reason);
            submission.setStatus(ComplianceSubmission.SubmissionStatus.REJECTED);
            submission.setCarrierRejectionReason(reason);
        } else {
            carrierSubmission.setStatus(CarrierSubmission.SubmissionStatus.REVIEWING);
        }

        carrierSubmissionRepository.save(carrierSubmission);
        submissionRepository.save(submission);

        log.info("Processed carrier callback for submission {}: status={}, reason={}",
                submissionId, status, reason);
    }

    /**
     * Get a submission with access check.
     *
     * @param submissionId The submission ID
     * @param userId The user ID of the requester
     * @return The submission entity
     * @throws ResourceNotFoundException if the submission doesn't exist
     * @throws AccessDeniedException if the user doesn't have access
     */
    private ComplianceSubmission getSubmissionWithAccessCheck(String submissionId, String userId) {
        ComplianceSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

        // Check user access
        if (!submission.getUserId().equals(userId)) {
            throw new AccessDeniedException("User does not have access to this submission");
        }

        return submission;
    }

    /**
     * Map a DTO to an entity.
     *
     * @param dto The submission DTO
     * @param userId The user ID of the creator
     * @return The submission entity
     */
    private ComplianceSubmission mapDtoToEntity(ComplianceSubmissionDTO dto, String userId) {
        return ComplianceSubmission.builder()
                .userId(userId)
                .companyId(userId) // This could be different in a multi-company setup
                .businessName(dto.getBusinessName())
                .businessType(dto.getBusinessType())
                .useCase(dto.getUseCase())
                .optInMethod(dto.getOptInMethod())
                .optInMethodDescription(dto.getOptInMethodDescription())
                .websiteUrl(dto.getWebsiteUrl())
                .includesSubscriptionServices(dto.getIncludesSubscriptionServices())
                .includesMarketing(dto.getIncludesMarketing())
                .build();
    }

    /**
     * Map an entity to a full DTO.
     *
     * @param entity The submission entity
     * @return The submission DTO
     */
    private ComplianceSubmissionDTO mapEntityToDto(ComplianceSubmission entity) {
        // Get messages
        List<SubmissionMessage> messages = messageRepository.findBySubmissionId(entity.getId());
        List<ComplianceSubmissionDTO.SampleMessageDTO> messageDTOs = messages.stream()
                .map(message -> ComplianceSubmissionDTO.SampleMessageDTO.builder()
                        .id(message.getId())
                        .messageText(message.getMessageText())
                        .compliant(message.getCompliant())
                        .matchesUseCase(message.getMatchesUseCase())
                        .hasRequiredElements(message.getHasRequiredElements())
                        .missingElements(message.getMissingElements())
                        .issues(message.getIssues())
                        .suggestedRevision(message.getSuggestedRevision())
                        .build())
                .collect(Collectors.toList());

        // Get images
        List<SubmissionImage> images = imageRepository.findBySubmissionId(entity.getId());
        List<ComplianceSubmissionDTO.SubmissionImageDTO> imageDTOs = images.stream()
                .map(image -> ComplianceSubmissionDTO.SubmissionImageDTO.builder()
                        .id(image.getId())
                        .imageUrl(image.getImageUrl())
                        .imageType(image.getImageType())
                        .fileName(image.getFileName())
                        .fileSize(image.getFileSize())
                        .optInType(image.getOptInType())
                        .description(image.getDescription())
                        .hasRequiredElements(image.getHasRequiredElements())
                        .detectedElements(image.getDetectedElements())
                        .missingElements(image.getMissingElements())
                        .textQuality(image.getTextQuality())
                        .complianceScore(image.getComplianceScore())
                        .recommendations(image.getRecommendations())
                        .build())
                .collect(Collectors.toList());

        // Get documents
        List<SubmissionDocument> documents = documentRepository.findBySubmissionId(entity.getId());
        List<ComplianceSubmissionDTO.SubmissionDocumentDTO> documentDTOs = documents.stream()
                .map(document -> ComplianceSubmissionDTO.SubmissionDocumentDTO.builder()
                        .id(document.getId())
                        .documentUrl(document.getDocumentUrl())
                        .documentType(document.getDocumentType())
                        .fileName(document.getFileName())
                        .fileSize(document.getFileSize())
                        .description(document.getDescription())
                        .compliant(document.getCompliant())
                        .hasRequiredElements(document.getHasRequiredElements())
                        .detectedElements(document.getDetectedElements())
                        .missingElements(document.getMissingElements())
                        .contentIssues(document.getContentIssues())
                        .complianceScore(document.getComplianceScore())
                        .recommendations(document.getRecommendations())
                        .build())
                .collect(Collectors.toList());

        return ComplianceSubmissionDTO.builder()
                .id(entity.getId())
                .businessName(entity.getBusinessName())
                .businessType(entity.getBusinessType())
                .useCase(entity.getUseCase())
                .optInMethod(entity.getOptInMethod())
                .optInMethodDescription(entity.getOptInMethodDescription())
                .websiteUrl(entity.getWebsiteUrl())
                .includesSubscriptionServices(entity.getIncludesSubscriptionServices())
                .includesMarketing(entity.getIncludesMarketing())
                .status(entity.getStatus() != null ? entity.getStatus().toString() : null)
                .complianceScore(entity.getComplianceScore())
                .verificationId(entity.getVerificationId())
                .carrierSubmissionId(entity.getCarrierSubmissionId())
                .carrierRejectionReason(entity.getCarrierRejectionReason())
                .sampleMessages(messageDTOs)
                .images(imageDTOs)
                .documents(documentDTOs)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Map an entity to a basic DTO (without related entities).
     *
     * @param entity The submission entity
     * @return The basic submission DTO
     */
    private ComplianceSubmissionDTO mapEntityToBasicDto(ComplianceSubmission entity) {
        return ComplianceSubmissionDTO.builder()
                .id(entity.getId())
                .businessName(entity.getBusinessName())
                .businessType(entity.getBusinessType())
                .status(entity.getStatus() != null ? entity.getStatus().toString() : null)
                .complianceScore(entity.getComplianceScore())
                .verificationId(entity.getVerificationId())
                .carrierSubmissionId(entity.getCarrierSubmissionId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Get the file extension from a filename.
     *
     * @param fileName The filename
     * @return The file extension (with dot)
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
}
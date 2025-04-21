package com.salesmsg.compliance.controller;

import com.salesmsg.compliance.dto.ComplianceSubmissionDTO;
import com.salesmsg.compliance.dto.SubmissionStatusDTO;
import com.salesmsg.compliance.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller for managing 10DLC compliance submissions.
 */
@RestController
@RequestMapping("/compliance/submissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Submissions", description = "Compliance submission operations")
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(summary = "Create a new compliance submission")
    @PostMapping
    public ResponseEntity<ComplianceSubmissionDTO> createSubmission(
            @Valid @RequestBody ComplianceSubmissionDTO submissionDTO,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Creating new submission for user: {}", userDetails.getUsername());
        ComplianceSubmissionDTO created = submissionService.createSubmission(submissionDTO, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Get submission by ID")
    @GetMapping("/{submissionId}")
    public ResponseEntity<ComplianceSubmissionDTO> getSubmission(
            @PathVariable String submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Fetching submission: {} for user: {}", submissionId, userDetails.getUsername());
        ComplianceSubmissionDTO submission = submissionService.getSubmission(submissionId, userDetails.getUsername());
        return ResponseEntity.ok(submission);
    }

    @Operation(summary = "Update an existing submission")
    @PutMapping("/{submissionId}")
    public ResponseEntity<ComplianceSubmissionDTO> updateSubmission(
            @PathVariable String submissionId,
            @Valid @RequestBody ComplianceSubmissionDTO submissionDTO,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Updating submission: {} for user: {}", submissionId, userDetails.getUsername());
        ComplianceSubmissionDTO updated = submissionService.updateSubmission(submissionId, submissionDTO, userDetails.getUsername());
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete a submission")
    @DeleteMapping("/{submissionId}")
    public ResponseEntity<Void> deleteSubmission(
            @PathVariable String submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Deleting submission: {} for user: {}", submissionId, userDetails.getUsername());
        submissionService.deleteSubmission(submissionId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List submissions with optional filtering")
    @GetMapping
    public ResponseEntity<List<ComplianceSubmissionDTO>> listSubmissions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Listing submissions for user: {}, status: {}, page: {}, limit: {}",
                userDetails.getUsername(), status, page, limit);
        List<ComplianceSubmissionDTO> submissions = submissionService.listSubmissions(
                userDetails.getUsername(), status, page, limit);
        return ResponseEntity.ok(submissions);
    }

    @Operation(summary = "Upload a document for a submission")
    @PostMapping(value = "/{submissionId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplianceSubmissionDTO.SubmissionDocumentDTO> uploadDocument(
            @PathVariable String submissionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Uploading document for submission: {}, type: {}", submissionId, documentType);
        ComplianceSubmissionDTO.SubmissionDocumentDTO document = submissionService.addDocument(
                submissionId, file, documentType, description, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @Operation(summary = "Upload an image for a submission")
    @PostMapping(value = "/{submissionId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplianceSubmissionDTO.SubmissionImageDTO> uploadImage(
            @PathVariable String submissionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("imageType") String imageType,
            @RequestParam("optInType") String optInType,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Uploading image for submission: {}, type: {}, optInType: {}",
                submissionId, imageType, optInType);
        ComplianceSubmissionDTO.SubmissionImageDTO image = submissionService.addImage(
                submissionId, file, imageType, optInType, description, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(image);
    }

    @Operation(summary = "Submit to carrier after verification")
    @PostMapping("/{submissionId}/submit")
    public ResponseEntity<SubmissionStatusDTO> submitToCarrier(
            @PathVariable String submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Submitting to carrier: {}", submissionId);
        SubmissionStatusDTO status = submissionService.submitToCarrier(submissionId, userDetails.getUsername());
        return ResponseEntity.accepted().body(status);
    }
}
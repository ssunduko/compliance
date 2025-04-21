package com.salesmsg.compliance.controller;

import com.salesmsg.compliance.dto.ComplianceReportDTO;
import com.salesmsg.compliance.dto.VerificationDTO;
import com.salesmsg.compliance.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing 10DLC compliance verification processes.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Verification", description = "Compliance verification operations")
public class VerificationController {

    private final VerificationService verificationService;

    @Operation(summary = "Initiate verification for a submission")
    @PostMapping("/compliance/submissions/{submissionId}/verify")
    public ResponseEntity<VerificationDTO> startVerification(
            @PathVariable String submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Starting verification for submission: {}", submissionId);
        VerificationDTO verification = verificationService.startVerification(submissionId, userDetails.getUsername());
        return ResponseEntity.accepted().body(verification);
    }

    @Operation(summary = "Get verification status")
    @GetMapping("/compliance/verifications/{verificationId}")
    public ResponseEntity<VerificationDTO> getVerificationStatus(
            @PathVariable String verificationId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Fetching verification status: {}", verificationId);
        VerificationDTO status = verificationService.getVerificationStatus(verificationId, userDetails.getUsername());
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Get compliance report for a submission")
    @GetMapping("/compliance/submissions/{submissionId}/report")
    public ResponseEntity<ComplianceReportDTO> getComplianceReport(
            @PathVariable String submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Fetching compliance report for submission: {}", submissionId);
        ComplianceReportDTO report = verificationService.getComplianceReport(submissionId, userDetails.getUsername());
        return ResponseEntity.ok(report);
    }

    @Operation(summary = "Cancel an ongoing verification")
    @DeleteMapping("/compliance/verifications/{verificationId}")
    public ResponseEntity<Void> cancelVerification(
            @PathVariable String verificationId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Cancelling verification: {}", verificationId);
        verificationService.cancelVerification(verificationId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
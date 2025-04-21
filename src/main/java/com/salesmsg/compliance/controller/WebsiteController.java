package com.salesmsg.compliance.controller;

import com.salesmsg.compliance.dto.WebsiteCheckDTO;
import com.salesmsg.compliance.service.WebsiteValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for website compliance checking services.
 */
@RestController
@RequestMapping("/compliance/check/website")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Website Validation", description = "Website compliance validation")
public class WebsiteController {

    private final WebsiteValidationService websiteValidationService;

    @Operation(summary = "Check website for SMS compliance requirements")
    @PostMapping
    public ResponseEntity<WebsiteCheckDTO> checkWebsite(
            @Valid @RequestBody WebsiteCheckDTO request) {

        log.info("Checking website compliance for URL: {}", request.getUrl());

        WebsiteCheckDTO result = websiteValidationService.checkWebsite(request);
        return ResponseEntity.ok(result);
    }
}
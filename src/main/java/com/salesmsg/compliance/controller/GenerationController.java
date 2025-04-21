package com.salesmsg.compliance.controller;

import com.salesmsg.compliance.dto.MessageGenerationDTO;
import com.salesmsg.compliance.dto.UseCaseGenerationDTO;
import com.salesmsg.compliance.service.ContentGenerationService;
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
 * Controller for AI-driven content generation services.
 */
@RestController
@RequestMapping("/compliance/generate")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Content Generation", description = "AI-powered compliant content generation")
public class GenerationController {

    private final ContentGenerationService contentGenerationService;

    @Operation(summary = "Generate compliant sample messages")
    @PostMapping("/sample-messages")
    public ResponseEntity<MessageGenerationDTO.Response> generateSampleMessages(
            @Valid @RequestBody MessageGenerationDTO.Request request) {

        log.info("Generating sample messages for use case: {}, count: {}",
                request.getUseCase(), request.getMessageCount());

        MessageGenerationDTO.Response result = contentGenerationService.generateSampleMessages(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Generate compliant use case description")
    @PostMapping("/use-case")
    public ResponseEntity<UseCaseGenerationDTO.Response> generateUseCase(
            @Valid @RequestBody UseCaseGenerationDTO.Request request) {

        log.info("Generating use case description for businessType: {}, messagingPurpose: {}",
                request.getBusinessType(), request.getMessagingPurpose());

        UseCaseGenerationDTO.Response result = contentGenerationService.generateUseCase(request);
        return ResponseEntity.ok(result);
    }
}
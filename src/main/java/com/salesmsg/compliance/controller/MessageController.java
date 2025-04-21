package com.salesmsg.compliance.controller;

import com.salesmsg.compliance.dto.MessageValidationDTO;
import com.salesmsg.compliance.service.MessageValidationService;
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
 * Controller for SMS message validation services.
 */
@RestController
@RequestMapping("/compliance/check/messages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Message Validation", description = "SMS message compliance validation")
public class MessageController {

    private final MessageValidationService messageValidationService;

    @Operation(summary = "Validate SMS messages for compliance")
    @PostMapping
    public ResponseEntity<MessageValidationDTO> validateMessages(
            @Valid @RequestBody MessageValidationDTO request) {

        log.info("Validating {} messages for use case: {}",
                request.getMessages().size(), request.getUseCase());

        MessageValidationDTO result = messageValidationService.validateMessages(request);
        return ResponseEntity.ok(result);
    }
}
package com.salesmsg.compliance.controller;

import com.salesmsg.compliance.dto.ImageAnalysisDTO;
import com.salesmsg.compliance.service.ImageAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for image compliance analysis services.
 */
@RestController
@RequestMapping("/compliance/check/image")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Image Analysis", description = "Image compliance analysis")
public class ImageController {

    private final ImageAnalysisService imageAnalysisService;

    @Operation(summary = "Analyze image for SMS compliance")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageAnalysisDTO> analyzeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("optInType") String optInType,
            @RequestParam(value = "description", required = false) String description) {

        log.info("Analyzing image for optInType: {}, size: {} bytes",
                optInType, image.getSize());

        ImageAnalysisDTO result = imageAnalysisService.analyzeImage(image, optInType, description);
        return ResponseEntity.ok(result);
    }
}
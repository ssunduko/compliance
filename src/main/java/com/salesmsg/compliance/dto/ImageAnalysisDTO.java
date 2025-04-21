package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for image compliance analysis requests and responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageAnalysisDTO {

    private String imageId;
    private String optInType;  // webform, paper_form, app_screenshot, etc.
    private String description;

    // Response fields
    private Boolean hasRequiredElements;
    private List<String> detectedElements;
    private List<String> missingElements;
    private String textQuality;  // excellent, good, poor, unreadable
    private Float complianceScore;
    private List<String> recommendations;
}
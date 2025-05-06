package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.ImageAnalysisDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TextractImageAnalysisService.
 * Tests the analysis of images for 10DLC compliance using AWS Textract.
 */
@SpringBootTest
@ActiveProfiles("test")
public class TextractImageAnalysisServiceTest {

    @Autowired
    private TextractImageAnalysisService textractImageAnalysisService;

    /**
     * Test analysis of a web form image using Textract.
     */
    @Test
    void testAnalyzeWebFormImage() throws IOException {
        // Prepare a mock image file
        MockMultipartFile imageFile = createMockImageFile("form.png", "webform");

        // Execute the analysis
        ImageAnalysisDTO result = textractImageAnalysisService.analyzeImage(
                imageFile, "webform", "Sample opt-in webform for testing");

        // Verify result
        assertNotNull(result);
        assertEquals("webform", result.getOptInType());
        assertEquals("Sample opt-in webform for testing", result.getDescription());
        assertNotNull(result.getImageId());

        // Basic validation - exact values depend on the AI service response
        assertNotNull(result.getHasRequiredElements());
        assertNotNull(result.getComplianceScore());

        System.out.println("Compliance Score: " + result.getComplianceScore());
        System.out.println("Text Quality: " + result.getTextQuality());

        System.out.println("Detected Elements:");
        if (result.getDetectedElements() != null) {
            result.getDetectedElements().forEach(element -> System.out.println("- " + element));
        }

        System.out.println("Missing Elements:");
        if (result.getMissingElements() != null) {
            result.getMissingElements().forEach(element -> System.out.println("- " + element));
        }

        System.out.println("Recommendations:");
        if (result.getRecommendations() != null) {
            result.getRecommendations().forEach(rec -> System.out.println("- " + rec));
        }

        // Basic verification - the exact values will depend on the actual image
        // We're primarily ensuring the service returns a populated result
        assertTrue(result.getComplianceScore() >= 60.0f,
                "Compliant webform should have a reasonable compliance score");
    }

    /**
     * Test analysis of a paper form image using Textract.
     */
    @Test
    void testAnalyzePaperFormImage() throws IOException {
        // Prepare a mock image file
        MockMultipartFile imageFile = createMockImageFile("paper_form.jpg", "paper_form");

        // Execute the analysis
        ImageAnalysisDTO result = textractImageAnalysisService.analyzeImage(
                imageFile, "paper_form", "Paper consent form for SMS messaging");

        // Verify result
        assertNotNull(result);
        assertEquals("paper_form", result.getOptInType());

        // Basic validation of text extraction and analysis results
        assertNotNull(result.getHasRequiredElements());
        assertNotNull(result.getTextQuality());
        assertNotNull(result.getComplianceScore());

        // Print detailed results for debugging
        System.out.println("Paper Form Analysis Results:");
        System.out.println("Compliance Score: " + result.getComplianceScore());
        System.out.println("Text Quality: " + result.getTextQuality());

        // For paper forms, text quality assessment is particularly important
        assertNotNull(result.getTextQuality(),
                "Text quality should be assessed for paper forms");
    }

    /**
     * Test analysis of an image with poor text quality.
     */
    @Test
    void testAnalyzeImageWithPoorTextQuality() throws IOException {
        // Prepare a mock image file with low resolution/quality
        MockMultipartFile imageFile = createMockImageFile("low_quality.jpg", "webform");

        // Execute the analysis
        ImageAnalysisDTO result = textractImageAnalysisService.analyzeImage(
                imageFile, "webform", "Low quality image of consent form");

        // Verify result
        assertNotNull(result);

        // For poor quality images, we expect:
        // - Low compliance score
        // - Text quality rating of "poor" or "unreadable"
        // - Recommendations about image quality

        System.out.println("Low Quality Image Results:");
        System.out.println("Compliance Score: " + result.getComplianceScore());
        System.out.println("Text Quality: " + result.getTextQuality());

        if (result.getTextQuality() != null) {
            boolean isPoorQuality = result.getTextQuality().equals("poor")
                    || result.getTextQuality().equals("unreadable");

            // Only assert if we have actual low-quality test images
            if (Files.exists(Paths.get("src/test/resources/images/low_quality.jpg"))) {
                assertTrue(isPoorQuality,
                        "Low quality image should be assessed as 'poor' or 'unreadable'");
            }
        }
    }

    /**
     * Helper method to create a mock image file for testing.
     * If the file exists on disk, it will use real content.
     * Otherwise, it creates a dummy file.
     */
    private MockMultipartFile createMockImageFile(String filename, String type) throws IOException {
        // Try to find the file in test resources
        Path resourcePath = Paths.get("src/test/resources/images/" + filename);

        byte[] content;
        if (Files.exists(resourcePath)) {
            // Use real file content if available
            content = Files.readAllBytes(resourcePath);
        } else {
            // Use dummy content if file doesn't exist
            content = "fake image content".getBytes();
        }

        return new MockMultipartFile(
                "image",
                filename,
                type.equals("webform") ? "image/png" : "image/jpeg",
                content
        );
    }
}
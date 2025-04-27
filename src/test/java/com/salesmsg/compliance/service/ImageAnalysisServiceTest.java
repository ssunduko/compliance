package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.ImageAnalysisDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ImageAnalysisService.
 * Tests the analysis of images for 10DLC compliance.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ImageAnalysisServiceTest {

    @Autowired
    private ImageAnalysisService imageAnalysisService;

    @Autowired
    private AWSService awsService;

    /**
     * Test analysis of a web form image.
     */
    @Test
    void testAnalyzeWebFormImage() throws IOException {
        // Prepare a mock image file
        MockMultipartFile imageFile = createMockImageFile("form.png", "webform");

        // Execute the analysis
        ImageAnalysisDTO result = imageAnalysisService.analyzeImage(
                imageFile, "webform", "Sample opt-in webform for testing");

        // Verify result
        assertNotNull(result);
        assertEquals("webform", result.getOptInType());
        assertEquals("Sample opt-in webform for testing", result.getDescription());
        assertNotNull(result.getImageId());

        // Basic validation - exact values depend on the AI service response
        assertNotNull(result.getHasRequiredElements());
        assertNotNull(result.getComplianceScore());

        System.out.println(result.getComplianceScore());
        result.getRecommendations().forEach(System.out::println);
        result.getDetectedElements().forEach(System.out::println);
        result.getMissingElements().forEach(System.out::println);

        // Webform should have detectable elements
        if (result.getDetectedElements() != null) {
            assertFalse(result.getDetectedElements().isEmpty(),
                    "Web form should have detectable elements");
        }

        // Basic verification - the exact values will depend on the actual website
        // We're primarily ensuring the service returns a populated result
        assertTrue(result.getComplianceScore() >= 60.0f,
                "Compliant website should have a high compliance score");
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
                "form.png",
                filename,
                "image/png",
                content
        );
    }
}
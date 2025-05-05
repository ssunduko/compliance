package com.salesmsg.compliance.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.salesmsg.compliance.dto.WebsiteCheckDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FirecrawlWebsiteValidationServiceTest {


    @Autowired
    private FirecrawlWebsiteValidationService service;

    @Test
    void checkWebsite_RealWebsite_Success() {
        // Arrange
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://adocuscto.com/")
                .checkWebform(true)
                .webformUrl("https://adocuscto.com/privacy-policy")
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getComplianceScore());
        assertTrue(result.getComplianceScore() >= 0);
        assertTrue(result.getComplianceScore() <= 100);

        System.out.println("Compliance Score: " + result.getComplianceScore());
        result.getIssues().forEach(System.out::println);

        // Check if issues were identified
        assertNotNull(result.getIssues());
        result.getIssues().forEach(issue -> {
            assertNotNull(issue.getSeverity());
            assertNotNull(issue.getDescription());
            assertNotNull(issue.getRecommendation());
        });

        // We expect a compliant site to have a working webform with required elements
        if (Boolean.TRUE.equals(result.getWebformFunctional())) {
            assertNotNull(result.getWebformHasRequiredElements(),
                    "Webform elements should be evaluated");
        }

    }

    @Test
    void checkWebsite_WithWebform_Success() {
        // Arrange
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://www.salesforce.com")
                .checkWebform(true)
                .webformUrl("https://www.salesforce.com/form/signup/")  // Example signup form
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getComplianceScore());
        assertEquals("https://www.salesforce.com", result.getUrl());
        assertEquals("https://www.salesforce.com/form/signup/", result.getWebformUrl());
        assertTrue(result.getCheckWebform());

        // Check if webform analysis was performed
        assertNotNull(result.getWebformFunctional());
        assertNotNull(result.getWebformHasRequiredElements());
    }

    @Test
    void checkWebsite_InvalidUrl_ReturnsError() {
        // Arrange
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://invalid-website-that-does-not-exist-12345.com")
                .checkWebform(false)
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertEquals(0f, result.getComplianceScore());
        assertFalse(result.getIssues().isEmpty());
        assertEquals("critical", result.getIssues().get(0).getSeverity());
        assertTrue(result.getIssues().get(0).getDescription().contains("Error"));
    }

    @Test
    void checkWebsite_EmptyUrl_ReturnsError() {
        // Arrange
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("")
                .checkWebform(false)
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertEquals(0f, result.getComplianceScore());
        assertFalse(result.getIssues().isEmpty());
        assertEquals("critical", result.getIssues().get(0).getSeverity());
    }

    @Test
    void checkWebsite_ComplianceWebsite_HighScore() {
        // Arrange - Using a website that should have good SMS compliance
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://www.tcpa.com")  // TCPA compliance website
                .checkWebform(false)
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getComplianceScore());
        // A compliance-focused website should have a relatively high score
        assertTrue(result.getComplianceScore() > 50);
    }

    @Test
    void checkWebsite_NonComplianceWebsite_LowScore() {
        // Arrange - Using a basic website that likely doesn't have SMS compliance info
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://example.com")  // Basic example site
                .checkWebform(false)
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getComplianceScore());
        // A basic website without SMS info should have a lower score
        assertTrue(result.getComplianceScore() < 70);

        // Should have recommendations for improvement
        assertFalse(result.getIssues().isEmpty());
    }

    @Test
    void checkWebsite_NullRequest_ThrowsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            service.checkWebsite(null);
        });
    }

    @Test
    void checkWebsite_RealWorldMessagingPlatform() {
        // Arrange - Using a real messaging platform that should have compliance info
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://www.bandwidth.com")  // Messaging platform
                .checkWebform(false)
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getComplianceScore());

        // A messaging platform should have privacy policy
        assertTrue(result.getHasPrivacyPolicy());

        // Should identify SMS-related content
        if (result.getHasSmsDataSharingClause() != null) {
            assertTrue(result.getHasSmsDataSharingClause());
        }
    }

    @Test
    void checkWebsite_MultipleUrls_ExtractsFromAll() {
        // Arrange - Test with both main URL and webform URL
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://www.twilio.com")
                .checkWebform(true)
                .webformUrl("https://www.twilio.com/try-twilio")
                .build();

        // Act
        WebsiteCheckDTO result = service.checkWebsite(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getComplianceScore());
        assertTrue(result.getCheckWebform());
        assertEquals("https://www.twilio.com", result.getUrl());
        assertEquals("https://www.twilio.com/try-twilio", result.getWebformUrl());

        // Should have extracted from both URLs
        assertNotNull(result.getWebformFunctional());
    }
}

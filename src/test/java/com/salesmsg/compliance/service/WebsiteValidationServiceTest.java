package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.WebsiteCheckDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebsiteValidationService.
 * Tests the validation of websites for SMS compliance requirements.
 */
@SpringBootTest
@ActiveProfiles("test")
public class WebsiteValidationServiceTest {

    @Autowired
    private WebsiteValidationService websiteValidationService;

    /**
     * Test validation of a compliant website.
     */
    @Test
    void testCheckCompliantWebsite() {
        // Create a request with a URL that should have all required elements
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://ffcd7b8e.salesmsg-demo.pages.dev/contact_form")
                .checkWebform(true)
                .webformUrl("https://ffcd7b8e.salesmsg-demo.pages.dev/privacy_policy")
                .build();

        // Execute
        WebsiteCheckDTO result = websiteValidationService.checkWebsite(request);

        // Verify
        assertNotNull(result);

        System.out.println("Compliance Score: " + result.getComplianceScore());
        result.getIssues().forEach(System.out::println);

        // Basic verification - the exact values will depend on the actual website
        // We're primarily ensuring the service returns a populated result
        assertTrue(result.getComplianceScore() >= 60.0f,
                "Compliant website should have a high compliance score");

        // Verify privacy policy detection
        assertNotNull(result.getHasPrivacyPolicy(),
                "Privacy policy detection should provide a result");

        // We expect a compliant site to have a working webform with required elements
        if (Boolean.TRUE.equals(result.getWebformFunctional())) {
            assertNotNull(result.getWebformHasRequiredElements(),
                    "Webform elements should be evaluated");
        }

        // A compliant site should have few or no critical issues
        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            boolean hasCriticalIssues = result.getIssues().stream()
                    .anyMatch(issue -> "critical".equals(issue.getSeverity()));
            assertFalse(hasCriticalIssues,
                    "Compliant website should not have critical issues");
        }
    }

    /**
     * Test validation of a non-compliant website.
     */
    @Test
    void testCheckNonCompliantWebsite() {
        // Create a request with a URL that would be missing required elements
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("http://se498.s3-website-us-west-2.amazonaws.com/")
                .checkWebform(true)
                .webformUrl("http://se498.s3-website-us-west-2.amazonaws.com/")
                .build();

        // Execute
        WebsiteCheckDTO result = websiteValidationService.checkWebsite(request);

        // Verify
        assertNotNull(result);

        // The score should reflect non-compliance
        assertTrue(result.getComplianceScore() < 60.0f,
                "Non-compliant website should have a lower compliance score");

        // Non-compliant site should have issues
        assertNotNull(result.getIssues());
        assertFalse(result.getIssues().isEmpty(),
                "Non-compliant website should have identified issues");

        // At least one issue should be critical or major
        boolean hasSeriousIssues = result.getIssues().stream()
                .anyMatch(issue ->
                        "critical".equals(issue.getSeverity()) ||
                                "major".equals(issue.getSeverity()));
        assertTrue(hasSeriousIssues,
                "Non-compliant website should have at least one critical or major issue");

        // Check for specific missing elements
        if (Boolean.FALSE.equals(result.getHasPrivacyPolicy())) {
            assertTrue(result.getIssues().stream()
                            .anyMatch(issue -> issue.getDescription().toLowerCase().contains("privacy")),
                    "Should flag missing privacy policy as an issue");
        }

        // A non-compliant site would have critical issues
        boolean hasCriticalIssues = result.getIssues().stream()
                .anyMatch(issue -> "critical".equals(issue.getSeverity()));
        assertTrue(hasCriticalIssues,
                "Non-compliant website should have critical issues");
    }

    /**
     * Test validation with a webform-only check.
     */
    @Test
    void testCheckWebformOnly() {
        // Create a request focusing only on the webform
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("https://adocuscto.com/")
                .checkWebform(true)
                .webformUrl("https://adocuscto.com/privacy-policy")
                .build();

        // Execute
        WebsiteCheckDTO result = websiteValidationService.checkWebsite(request);

        // Verify
        assertNotNull(result);
        assertNotNull(result.getWebformFunctional(),
                "Webform functionality should be checked");

        if (Boolean.TRUE.equals(result.getWebformFunctional())) {
            assertNotNull(result.getWebformHasRequiredElements(),
                    "Required elements in webform should be checked");
        }
    }

    /**
     * Test validation with website but no webform.
     */
    @Test
    void testCheckWebsiteWithoutWebform() {
        // Create a request without webform check
        WebsiteCheckDTO request = WebsiteCheckDTO.builder()
                .url("http://se498.s3-website-us-west-2.amazonaws.com/")
                .checkWebform(false)
                .build();

        // Execute
        WebsiteCheckDTO result = websiteValidationService.checkWebsite(request);

        // Verify
        assertNotNull(result);

        // Basic website elements should be checked
        assertNotNull(result.getHasPrivacyPolicy(),
                "Privacy policy should be checked");
        assertNotNull(result.getHasSmsDataSharingClause(),
                "SMS data sharing clause should be checked");
    }
}
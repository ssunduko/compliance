package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for validating use case descriptions against 10DLC compliance requirements.
 * Analyzes use case descriptions for clarity, alignment with business type,
 * and compliance with carrier guidelines.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UseCaseValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Parse an analysis result from the AI model.
     *
     * @param responseText The response text from the AI model
     * @return A map containing the parsed analysis result
     */
    public Map<String, Object> parseAnalysisResult(String responseText) {
        try {
            // Extract JSON from response (in case there's additional text around it)
            String jsonContent = extractJsonFromResponse(responseText);

            // Parse the JSON
            return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});

        } catch (JsonProcessingException e) {
            log.error("Error parsing use case analysis result", e);
            return Map.of(
                    "is_compliant", false,
                    "score", 0,
                    "issues", List.of(Map.of(
                            "severity", "critical",
                            "description", "Error analyzing use case: " + e.getMessage(),
                            "recommendation", "Please try again or contact support"
                    )),
                    "recommendations", List.of(Map.of(
                            "priority", "high",
                            "description", "Unable to analyze use case",
                            "action", "Please verify the use case text and try again"
                    )),
                    "reasoning", "Error during analysis"
            );
        }
    }

    /**
     * Validate a use case description against compliance requirements.
     *
     * @param useCase The use case description
     * @param businessType The business type
     * @param guidelines The carrier guidelines
     * @return A map containing the validation result
     */
    public Map<String, Object> validateUseCase(String useCase, String businessType, String guidelines) {
        log.info("Validating use case for business type: {}", businessType);

        // Note: This method would typically use the ChatClient to analyze the use case,
        // but since we're only implementing the parseAnalysisResult method as requested,
        // this is left as a placeholder for a complete implementation.

        return Map.of(
                "is_compliant", true,
                "score", 85,
                "issues", new ArrayList<>(),
                "recommendations", new ArrayList<>(),
                "reasoning", "Use case analysis would be performed here"
        );
    }

    /**
     * Check if a use case contains prohibited content.
     *
     * @param useCase The use case description
     * @return A map containing prohibited content findings
     */
    public Map<String, Object> checkForProhibitedContent(String useCase) {
        log.info("Checking use case for prohibited content");

        // Define common prohibited content categories for 10DLC
        Map<String, List<String>> prohibitedCategories = new HashMap<>();
        prohibitedCategories.put("gambling", List.of("casino", "bet", "gambling", "lottery", "poker"));
        prohibitedCategories.put("illegal_substances", List.of("drugs", "cannabis", "marijuana", "cocaine"));
        prohibitedCategories.put("adult_content", List.of("adult", "sexual", "explicit"));
        prohibitedCategories.put("phishing", List.of("verify account", "confirm details", "urgent action"));

        // Results map
        Map<String, Object> results = new HashMap<>();
        results.put("contains_prohibited_content", false);

        // Check use case against each category
        Map<String, Boolean> categoryResults = new HashMap<>();
        for (Map.Entry<String, List<String>> category : prohibitedCategories.entrySet()) {
            String categoryName = category.getKey();
            List<String> keywords = category.getValue();

            // Check for keywords in the use case (case-insensitive)
            String useCaseLower = useCase.toLowerCase();
            boolean containsCategory = keywords.stream()
                    .anyMatch(useCaseLower::contains);

            categoryResults.put(categoryName, containsCategory);

            // If any category is detected, mark as prohibited
            if (containsCategory) {
                results.put("contains_prohibited_content", true);
            }
        }

        results.put("category_results", categoryResults);
        return results;
    }

    /**
     * Extract JSON content from a text response that might contain additional text.
     *
     * @param response The response text that should contain a JSON object
     * @return The extracted JSON string
     */
    private String extractJsonFromResponse(String response) {
        // Look for JSON pattern using regex
        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group();
        }

        // If no JSON found, return the original response
        return response;
    }
}
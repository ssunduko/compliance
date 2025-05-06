package com.salesmsg.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmsg.compliance.dto.ImageAnalysisDTO;
import com.salesmsg.compliance.dto.TextractAnalysisResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing images for 10DLC compliance using AWS Textract.
 * Extracts text and form elements from opt-in forms and analyzes them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextractImageAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AWSService awsService;
    private final TextractClient textractClient;

    private static final String IMAGE_ANALYSIS_PROMPT = """
            You are an SMS Compliance Image Analyzer specialized in evaluating opt-in forms and consent mechanisms.
            Your task is to analyze the extracted content from an image for compliance with 10DLC regulations.
            
            Opt-In Type: %s
            Description: %s
            
            Extracted Text:
            %s
            
            Form Elements Detected:
            %s
            
            Analyze the content for the following elements:
            1. Checkbox or consent indication for SMS
            2. Clear consent text explaining messaging purpose
            3. Opt-out instructions (STOP keyword)
            4. Terms and conditions reference
            5. Privacy policy reference
            6. Message frequency disclosure
            7. Clear business identification
            8. Message and data rates disclosure
            
            Required elements for %s opt-in type:
            - Webform: consent indication, consent text, terms reference, opt-out instructions
            - Paper form: consent indication, consent text, opt-out instructions
            - App screenshot: visible consent UI, terms reference, opt-out instructions
            - Confirmation page: confirmation message, terms reference, opt-out instructions
            
            Return your analysis in JSON format:
            {
              "hasRequiredElements": boolean,
              "detectedElements": [string],
              "missingElements": [string],
              "textQuality": "excellent" | "good" | "poor" | "unreadable",
              "complianceScore": float (0-100),
              "recommendations": [string]
            }
            """;

    /**
     * Analyze an image for compliance with 10DLC requirements using AWS Textract.
     *
     * @param image The image file to analyze
     * @param optInType The type of opt-in being analyzed
     * @param description Optional description of the image
     * @return The analysis results
     */
    public ImageAnalysisDTO analyzeImage(MultipartFile image, String optInType, String description) {
        log.info("Analyzing image for opt-in type: {}, size: {} bytes", optInType, image.getSize());

        try {
            // Upload image to S3 to get a URL
            String imageKey = "temp/analysis/" + UUID.randomUUID() + getFileExtension(image.getOriginalFilename());
            String imageUrl = awsService.uploadFile(image, imageKey);

            // Extract content from the image using Textract
            TextractAnalysisResultDTO analysisResult = extractContentFromImage(image);

            String extractedText = analysisResult.getText();
            String formElementsDescription = analysisResult.getFormElementsDescription();

            // Format the system prompt with request parameters and extracted content
            String systemPrompt = String.format(
                    IMAGE_ANALYSIS_PROMPT,
                    optInType,
                    description != null ? description : "No description provided",
                    extractedText,
                    formElementsDescription,
                    optInType
            );

            // Create the user message
            String userPrompt = "Please analyze the extracted content for 10DLC compliance.";

            // Create the prompt
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(systemPrompt));
            promptMessages.add(new UserMessage(userPrompt));
            Prompt prompt = new Prompt(promptMessages);

            // Execute the chat completion
            ChatResponse response = chatClient
                    .prompt(prompt)
                    .call()
                    .chatResponse();

            String responseText = response.getResult().getOutput().getText();

            // Delete the temporary image from S3
            awsService.deleteFile(imageKey);

            // Parse the JSON response
            Map<String, Object> finalResult = parseJsonResponse(responseText);

            // Build and return the DTO
            return buildImageAnalysisDTO(finalResult, imageUrl, optInType, description);

        } catch (Exception e) {
            log.error("Error analyzing image", e);
            throw new RuntimeException("Failed to analyze image: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text and form elements from an image using AWS Textract.
     *
     * @param image The image file to analyze
     * @return The extracted text and form elements
     * @throws IOException If the image cannot be processed
     */
    private TextractAnalysisResultDTO extractContentFromImage(MultipartFile image) throws IOException {
        // Read the image content
        SdkBytes imageBytes = SdkBytes.fromInputStream(image.getInputStream());

        // Create document with image bytes
        Document document = Document.builder()
                .bytes(imageBytes)
                .build();

        // Use AnalyzeDocument API for both text and form extraction
        AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder()
                .document(document)
                .featureTypes(FeatureType.FORMS, FeatureType.TABLES)
                .build();

        // Call Textract service
        AnalyzeDocumentResponse result = textractClient.analyzeDocument(request);

        // Process the results to extract text
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder formElementsBuilder = new StringBuilder();

        Map<String, String> selectionElements = new HashMap<>();
        Map<String, List<String>> formFields = new HashMap<>();

        // First pass - collect all blocks by their IDs
        Map<String, Block> blockMap = new HashMap<>();
        for (Block block : result.blocks()) {
            blockMap.put(block.id(), block);
        }

        // Second pass - process blocks by type
        for (Block block : result.blocks()) {
            switch (block.blockType()) {
                case LINE:
                    if (!textBuilder.isEmpty()) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(block.text());
                    break;

                case SELECTION_ELEMENT:
                    // Process checkboxes and radio buttons
                    if (block.selectionStatus() != null) {
                        String status = block.selectionStatus().toString();
                        selectionElements.put(block.id(), status);

                        formElementsBuilder.append("Form Selection Element: ")
                                .append(status)
                                .append(" (ID: ")
                                .append(block.id())
                                .append(")\n");
                    }
                    break;

                case KEY_VALUE_SET:
                    // Process form fields (key-value pairs)
                    if (block.entityTypes() != null &&
                            block.entityTypes().contains(EntityType.KEY)) {

                        // Get the key text
                        String keyText = getTextFromRelationships(block, blockMap, RelationshipType.CHILD);

                        // Get the value block ID
                        String valueBlockId = getIdFromRelationships(block, RelationshipType.VALUE);

                        if (valueBlockId != null) {
                            Block valueBlock = blockMap.get(valueBlockId);
                            if (valueBlock != null) {
                                // Get the value text
                                String valueText = getTextFromRelationships(valueBlock, blockMap, RelationshipType.CHILD);

                                // Add to form fields map
                                formFields.computeIfAbsent(keyText, k -> new ArrayList<>())
                                        .add(valueText);

                                formElementsBuilder.append("Form Field: ")
                                        .append(keyText)
                                        .append(" = ")
                                        .append(valueText)
                                        .append("\n");
                            }
                        }
                    }
                    break;

                default:
                    // Ignore other block types
                    break;
            }
        }

        // Now connect selection elements to their labels
        // This requires additional analysis of the relationships between blocks
        for (Block block : result.blocks()) {
            if (block.blockType() == BlockType.SELECTION_ELEMENT) {
                // Find parent block that might contain the label relationship
                if (block.relationships() != null) {
                    for (Relationship relationship : block.relationships()) {
                        if (relationship.type() == RelationshipType.CHILD) {
                            // This selection element might have a label
                            // Analyze nearby text blocks to find likely labels
                            // (This is a simplified approach - production code would be more sophisticated)
                            for (Block textBlock : result.blocks()) {
                                if (textBlock.blockType() == BlockType.LINE &&
                                        isNearby(block, textBlock)) {

                                    formElementsBuilder.append("Checkbox with label: ")
                                            .append(textBlock.text())
                                            .append(" (")
                                            .append(selectionElements.getOrDefault(block.id(), "UNKNOWN"))
                                            .append(")\n");

                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add form fields summary
        formElementsBuilder.append("\nForm Fields Summary:\n");
        for (Map.Entry<String, List<String>> entry : formFields.entrySet()) {
            formElementsBuilder.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(String.join(", ", entry.getValue()))
                    .append("\n");
        }

        // Add checkbox summary
        formElementsBuilder.append("\nCheckboxes/Radio Buttons Summary:\n");
        for (Map.Entry<String, String> entry : selectionElements.entrySet()) {
            formElementsBuilder.append("- Checkbox ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n");
        }

        return new TextractAnalysisResultDTO(
                textBuilder.toString(),
                formElementsBuilder.toString(),
                formFields,
                selectionElements
        );
    }

    /**
     * Determine if two blocks are likely related (nearby on the page).
     */
    private boolean isNearby(Block block1, Block block2) {
        // Check if the blocks have geometry information
        if (block1.geometry() == null || block2.geometry() == null) {
            return false;
        }

        // Get bounding boxes
        BoundingBox box1 = block1.geometry().boundingBox();
        BoundingBox box2 = block2.geometry().boundingBox();

        // Simple proximity check - this can be refined
        double horizDist = Math.abs(box1.left() - box2.left());
        double vertDist = Math.abs(box1.top() - box2.top());

        // Blocks are nearby if they're within a certain distance
        // These thresholds can be tuned for better results
        return horizDist < 0.2 && vertDist < 0.05;
    }

    /**
     * Get text from related blocks.
     */
    private String getTextFromRelationships(Block block, Map<String, Block> blockMap, RelationshipType relType) {
        StringBuilder result = new StringBuilder();

        if (block.relationships() != null) {
            for (Relationship relationship : block.relationships()) {
                if (relationship.type() == relType) {
                    for (String id : relationship.ids()) {
                        Block relatedBlock = blockMap.get(id);
                        if (relatedBlock != null && relatedBlock.text() != null) {
                            if (!result.isEmpty()) {
                                result.append(" ");
                            }
                            result.append(relatedBlock.text());
                        }
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * Get an ID from a relationship.
     */
    private String getIdFromRelationships(Block block, RelationshipType relType) {
        if (block.relationships() != null) {
            for (Relationship relationship : block.relationships()) {
                if (relationship.type() == relType && !relationship.ids().isEmpty()) {
                    return relationship.ids().getFirst();
                }
            }
        }
        return null;
    }

    /**
     * Build an ImageAnalysisDTO from the analysis result.
     *
     * @param analysisResult The raw analysis result
     * @param imageUrl The URL of the analyzed image
     * @param optInType The type of opt-in
     * @param description The image description
     * @return The formatted DTO
     */
    private ImageAnalysisDTO buildImageAnalysisDTO(
            Map<String, Object> analysisResult,
            String imageUrl,
            String optInType,
            String description) {

        // Extract values from the analysis result
        Boolean hasRequiredElements = (Boolean) analysisResult.getOrDefault("hasRequiredElements", false);

        @SuppressWarnings("unchecked")
        List<String> detectedElements = (List<String>) analysisResult.getOrDefault("detectedElements", new ArrayList<>());

        @SuppressWarnings("unchecked")
        List<String> missingElements = (List<String>) analysisResult.getOrDefault("missingElements", new ArrayList<>());

        String textQuality = (String) analysisResult.getOrDefault("textQuality", "unknown");

        Float complianceScore = ((Number) analysisResult.getOrDefault("complianceScore", 0)).floatValue();

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) analysisResult.getOrDefault("recommendations", new ArrayList<>());

        // Build the DTO
        return ImageAnalysisDTO.builder()
                .imageId(imageUrl)
                .optInType(optInType)
                .description(description)
                .hasRequiredElements(hasRequiredElements)
                .detectedElements(detectedElements)
                .missingElements(missingElements)
                .textQuality(textQuality)
                .complianceScore(complianceScore)
                .recommendations(recommendations)
                .build();
    }

    private Map<String, Object> parseJsonResponse(String responseText) {
        try {
            // Extract JSON from response text more carefully
            String jsonContent = extractJsonFromResponse(responseText);

            if (jsonContent == null || jsonContent.isEmpty()) {
                log.error("Failed to extract JSON from response: {}", responseText);
                return Map.of(
                        "hasRequiredElements", false,
                        "detectedElements", List.of(),
                        "missingElements", List.of("Unable to parse AI response"),
                        "textQuality", "unknown",
                        "complianceScore", 0.0f,
                        "recommendations", List.of("Error parsing AI response")
                );
            }

            // Parse the JSON
            return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing AI response: {}", e.getMessage());
            log.debug("Original response: {}", responseText);
            return Map.of(
                    "hasRequiredElements", false,
                    "detectedElements", List.of(),
                    "missingElements", List.of("Unable to parse AI response"),
                    "textQuality", "unknown",
                    "complianceScore", 0.0f,
                    "recommendations", List.of("Error parsing AI response: " + e.getMessage())
            );
        }
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        // More robust JSON extraction
        // This uses a regex to find content between { and } that contains at least one ":"
        // which indicates a JSON key-value pair
        Pattern pattern = Pattern.compile("\\{(?:[^{}]|\"[^\"]*\")*:[^{}]*\\}");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group();
        }

        // If we can't find JSON with the regex, try a simpler approach
        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');

        if (startIdx >= 0 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }

        return null;
    }

    /**
     * Get the file extension from a filename.
     *
     * @param filename The filename
     * @return The file extension (with dot)
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg"; // Default extension
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
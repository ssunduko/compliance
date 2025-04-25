package com.salesmsg.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.kendra.KendraClient;
import software.amazon.awssdk.services.kendra.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Amazon Kendra for retrieval of compliance-related information.
 * Used to retrieve carrier guidelines and other compliance documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KendraService {

    private final KendraClient kendraClient;

    @Value("${aws.kendra.indexId}")
    private String kendraIndexId;

    /**
     * Retrieve carrier guidelines specific to a business type.
     *
     * @param businessType The business type
     * @return A string containing the relevant carrier guidelines
     */
    public String retrieveCarrierGuidelines(String businessType) {
        log.info("Retrieving carrier guidelines for business type: {}", businessType);

        try {
            // Create the query
            String query = "10DLC carrier guidelines for " + businessType;

            // Create attribute filter for document type
            AttributeFilter documentTypeFilter = AttributeFilter.builder()
                    .equalsTo(
                            DocumentAttribute.builder()
                                    .key("document_type")
                                    .value(
                                            DocumentAttributeValue.builder()
                                                    .stringValue("carrier_guideline")
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            // Create attribute filter for business types
            AttributeFilter businessTypeFilter = AttributeFilter.builder()
                    .containsAll(
                            DocumentAttribute.builder()
                                    .key("business_types")
                                    .value(
                                            DocumentAttributeValue.builder()
                                                    .stringListValue(List.of(businessType, "all"))
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            // Combine filters
            AttributeFilter combinedFilter = AttributeFilter.builder()
                    .andAllFilters(List.of(documentTypeFilter, businessTypeFilter))
                    .build();

            // Execute the query
            QueryRequest queryRequest = QueryRequest.builder()
                    .indexId(kendraIndexId)
                    .queryText(query)
                    .attributeFilter(combinedFilter)
                    .queryResultTypeFilter(QueryResultType.DOCUMENT)
                    .build();

            QueryResponse queryResponse = kendraClient.query(queryRequest);

            // Process the results
            StringBuilder guidelinesBuilder = new StringBuilder();
            guidelinesBuilder.append("Carrier Guidelines for ").append(businessType).append(":\n\n");

            List<QueryResultItem> resultItems = queryResponse.resultItems();
            if (resultItems == null || resultItems.isEmpty()) {
                log.warn("No carrier guidelines found for business type: {}", businessType);
                return getFallbackGuidelines(businessType);
            }

            // Extract text from the result items
            for (QueryResultItem item : resultItems) {
                // Add document title
                TextWithHighlights documentTitle = item.documentTitle();
                if (documentTitle != null && documentTitle.text() != null) {
                    guidelinesBuilder.append("### ").append(documentTitle.text()).append("\n\n");
                }

                // Add document excerpt or text
                TextWithHighlights documentExcerpt = item.documentExcerpt();
                if (documentExcerpt != null && documentExcerpt.text() != null) {
                    guidelinesBuilder.append(documentExcerpt.text()).append("\n\n");
                } else if (item.additionalAttributes() != null) {
                    for (AdditionalResultAttribute attr : item.additionalAttributes()) {
                        if ("DocumentText".equals(attr.key()) &&
                                attr.value() != null &&
                                attr.value().textWithHighlightsValue() != null) {
                            guidelinesBuilder.append(attr.value().textWithHighlightsValue().text()).append("\n\n");
                        }
                    }
                }
            }

            return guidelinesBuilder.toString();

        } catch (Exception e) {
            log.error("Error retrieving carrier guidelines", e);
            return getFallbackGuidelines(businessType);
        }
    }

    /**
     * Search for compliance documentation.
     *
     * @param query The search query
     * @param documentType Optional document type filter
     * @param maxResults Maximum number of results to return
     * @return A list of search results
     */
    public List<Map<String, String>> searchComplianceDocumentation(String query, String documentType, int maxResults) {
        log.info("Searching compliance documentation: {}, type: {}", query, documentType);

        try {
            // Build the query request
            QueryRequest.Builder requestBuilder = QueryRequest.builder()
                    .indexId(kendraIndexId)
                    .queryText(query)
                    .queryResultTypeFilter(QueryResultType.DOCUMENT)
                    .pageSize(maxResults);

            // Add document type filter if specified
            if (documentType != null && !documentType.isEmpty()) {
                requestBuilder.attributeFilter(
                        AttributeFilter.builder()
                                .equalsTo(
                                        DocumentAttribute.builder()
                                                .key("document_type")
                                                .value(
                                                        DocumentAttributeValue.builder()
                                                                .stringValue(documentType)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );
            }

            // Execute the query
            QueryResponse queryResponse = kendraClient.query(requestBuilder.build());

            // Process the results
            List<Map<String, String>> searchResults = new ArrayList<>();

            List<QueryResultItem> resultItems = queryResponse.resultItems();
            if (resultItems == null) {
                return searchResults;
            }

            for (QueryResultItem item : resultItems) {
                Map<String, String> resultMap = new HashMap<>();

                // Add document title
                TextWithHighlights documentTitle = item.documentTitle();
                if (documentTitle != null) {
                    resultMap.put("title", documentTitle.text());
                }

                // Add document excerpt
                TextWithHighlights documentExcerpt = item.documentExcerpt();
                if (documentExcerpt != null) {
                    resultMap.put("excerpt", documentExcerpt.text());
                }

                // Add document URI
                String documentUri = item.documentURI();
                if (documentUri != null) {
                    resultMap.put("uri", documentUri);
                }

                // Add document ID
                resultMap.put("id", item.id());

                searchResults.add(resultMap);
            }

            return searchResults;

        } catch (Exception e) {
            log.error("Error searching compliance documentation", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get fallback guidelines for a business type when Kendra doesn't return results.
     *
     * @param businessType The business type
     * @return Basic guidelines for the business type
     */
    private String getFallbackGuidelines(String businessType) {
        log.info("Using fallback guidelines for business type: {}", businessType);

        return "Carrier Guidelines for " + businessType + ":\n\n"
                + "1. General Requirements for All Business Types:\n"
                + "   - Clear business identification in all messages\n"
                + "   - Explicit opt-in from recipients before sending messages\n"
                + "   - Clear opt-out instructions (STOP) in messages\n"
                + "   - No prohibited content (gambling, adult content, illegal substances)\n"
                + "   - No deceptive marketing practices\n"
                + "   - Compliance with TCPA, CTIA, and carrier requirements\n\n"
                + "2. Specific Guidelines for " + businessType + ":\n"
                + "   - Clearly state the purpose of each message\n"
                + "   - Include business name in each message\n"
                + "   - Provide value in each message (information, alerts, confirmations)\n"
                + "   - Respect message frequency expectations\n"
                + "   - Maintain accurate opt-in records\n"
                + "   - Honor opt-out requests immediately\n\n"
                + "3. Best Practices:\n"
                + "   - Keep messages concise and to the point\n"
                + "   - Send messages during appropriate hours\n"
                + "   - Limit use of abbreviations and slang\n"
                + "   - Provide a way for customers to get more information\n"
                + "   - Test message delivery before full deployment\n";
    }
}
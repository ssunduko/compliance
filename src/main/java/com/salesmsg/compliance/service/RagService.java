package com.salesmsg.compliance.service;

import com.salesmsg.compliance.dto.RagQueryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Retrieval Augmented Generation (RAG) using PGVector store.
 * Replaces the KendraService for retrieving compliance-related information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("${spring.ai.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${spring.ai.rag.top-k:5}")
    private int topK;

    /**
     * Create a document retriever for the vector store.
     *
     * @return The configured document retriever
     */
    private VectorStoreDocumentRetriever createDocumentRetriever() {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();
    }

    /**
     * Retrieve carrier guidelines specific to a business type.
     *
     * @param businessType The business type
     * @return A string containing the relevant carrier guidelines
     */
    public String retrieveCarrierGuidelines(String businessType) {
        log.info("Retrieving carrier guidelines for business type: {}", businessType);

        // Create the query
        String query = "10DLC carrier guidelines for " + businessType;

        // Perform similarity search in vector store
        List<Document> documents = vectorStore.similaritySearch(query);

        if (documents.isEmpty()) {
            log.warn("No carrier guidelines found for business type: {}", businessType);
            return getFallbackGuidelines(businessType);
        }

        // Format the results
        StringBuilder guidelinesBuilder = new StringBuilder();
        guidelinesBuilder.append("Carrier Guidelines for ").append(businessType).append(":\n\n");

        for (Document document : documents) {
            guidelinesBuilder.append(document.getText()).append("\n\n");
        }

        return guidelinesBuilder.toString();
    }

    /**
     * Query the LLM with RAG using the vector store.
     *
     * @param question The user's question
     * @return The AI response with context from the knowledge base
     */
    public String queryLLM(String question) {
        log.info("Querying LLM with RAG: {}", question);

        VectorStoreDocumentRetriever documentRetriever = createDocumentRetriever();

        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();

        return chatClient.prompt()
                .advisors(retrievalAugmentationAdvisor)
                .user(question)
                .call()
                .content();
    }

    /**
     * Perform a direct similarity search in the vector store.
     *
     * @param query The query text
     * @return A list of matching documents
     */
    public List<Document> similaritySearch(String query) {
        log.info("Performing similarity search: {}", query);
        return vectorStore.similaritySearch(query);
    }

    /**
     * Search for compliance documentation.
     *
     * @param query The search query
     * @param filter Optional metadata filter
     * @param maxResults Maximum number of results to return
     * @return A DTO containing search results and metadata
     */
    public RagQueryDTO searchComplianceDocumentation(String query, String filter, int maxResults) {
        log.info("Searching compliance documentation: {}, filter: {}", query, filter);

        // Create the retriever
        VectorStoreDocumentRetriever retriever = createDocumentRetriever();

        // Perform the search
        List<Document> documents = retriever.retrieve(new Query(query));

        // Limit results
        if (documents.size() > maxResults) {
            documents = documents.subList(0, maxResults);
        }

        // Format the results
        List<RagQueryDTO.DocumentResult> results = documents.stream()
                .map(doc -> RagQueryDTO.DocumentResult.builder()
                        .content(doc.getText())
                        .relevanceScore(doc.getMetadata().containsKey("score") ?
                                Double.parseDouble(doc.getMetadata().get("score").toString()) : null)
                        .metadata(doc.getMetadata())
                        .build())
                .collect(Collectors.toList());

        return RagQueryDTO.builder()
                .query(query)
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Get fallback guidelines for a business type when vector store doesn't return results.
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
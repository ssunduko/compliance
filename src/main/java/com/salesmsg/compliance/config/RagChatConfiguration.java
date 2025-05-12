package com.salesmsg.compliance.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Configuration for a RAG-enabled chat client, which combines the LLM with
 * the vector store for compliance information retrieval.
 */
@Configuration
public class RagChatConfiguration {

    @Value("${spring.ai.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${spring.ai.rag.top-k:5}")
    private int topK;

    /**
     * Creates a chat client with RAG capabilities.
     */
    @Bean
    ChatClient ragChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    /**
     * Creates a retrieval augmentation advisor for enhancing prompts with
     * retrieved content from the vector store.
     */
    @Bean
    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore) {
        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();

        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }

    /**
     * Creates a chat memory for storing conversation history.
     */
    @Bean
    public ChatMemory ragChatMemory() {
        return new InMemoryChatMemory();
    }
}
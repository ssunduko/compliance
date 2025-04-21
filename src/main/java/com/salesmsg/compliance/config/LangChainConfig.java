package com.salesmsg.compliance.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Configuration for LangChain4j components used in the application.
 * Sets up the foundation models, embedding models, and retrieval-augmented generation.
 */
@Configuration
@Slf4j
public class LangChainConfig {

    @Value("${ai.model.bedrock.claude}")
    private String claudeModelId;

    @Value("${ai.model.bedrock.embedding}")
    private String embeddingModelId;

    /**
     * Configure the Claude Anthropic model from Bedrock for chat completions.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(BedrockRuntimeClient runtimeClient) {
        return BedrockChatModel.builder()
                .client(runtimeClient)
                .modelId(claudeModelId)
                .build();
    }

    /**
     * Configure the embedding model from Bedrock for vector representations.
     */
    @Bean
    public EmbeddingModel embeddingModel(BedrockRuntimeClient runtimeClient) {
        return BedrockCohereEmbeddingModel
                .builder()
                .client(runtimeClient)
                .region(Region.US_EAST_1)
                //.model("cohere.embed-multilingual-v3")
                .model(embeddingModelId)
                .inputType(BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY)
                .build();
    }

    /**
     * Configure an in-memory embedding store for storing document embeddings.
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * Configure a content retriever for RAG (Retrieval Augmented Generation).
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingModel embeddingModel,
                                             EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.6)
                .build();
    }

    /**
     * Configure default chat memory for conversation context.
     */
    @Bean
    public MessageWindowChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
    }
}
package com.salesmsg.compliance.config;

import dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class EmbeddingConfig {


    /*@Bean
    public BedrockCohereEmbeddingModel embeddingModel(BedrockRuntimeClient runtimeClient) {
        return BedrockCohereEmbeddingModel
                .builder()
                .client(runtimeClient)
                .inputType(BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY)
                .build();
    }

    @Bean
    public GemFireVectorStore vectorStore(BedrockCohereEmbeddingModel embeddingModel) {
        return GemFireVectorStore.builder((org.springframework.ai.embedding.EmbeddingModel) embeddingModel)
                .host("localhost")
                .port(7071)
                .indexName("my-vector-index")
                .initializeSchema(true)
                .build();
    }*/
}
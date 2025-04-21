package com.salesmsg.compliance.config;


import org.springframework.ai.bedrock.api.BedrockApi;
import org.springframework.ai.bedrock.claude.BedrockClaude3ChatClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI Chat Client.
 * Creates and configures the ChatClient bean used by various services.
 */
@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.bedrock.chat-model}")
    private String chatModel;

    @Value("${ai.model.temperature}")
    private float temperature;

    @Value("${ai.model.max-tokens}")
    private int maxTokens;

    /**
     * Creates a ChatClient bean using Amazon Bedrock's Claude model.
     *
     * @param bedrockApi The Bedrock API client
     * @return The configured ChatClient
     */
    @Bean
    public ChatClient chatClient(BedrockApi bedrockApi) {
        return new BedrockClaude3ChatClient(bedrockApi)
                .withModel(chatModel)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens);
    }
}
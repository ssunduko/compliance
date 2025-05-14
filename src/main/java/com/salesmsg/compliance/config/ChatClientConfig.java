package com.salesmsg.compliance.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class ChatClientConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.secret-key}")
    private String secretKey;

    @Value("${ai.model.bedrock.claude:us.anthropic.claude-3-7-sonnet-20250219-v1:0}")
    private String modelId;

    @Value("${ai.model.temperature:0.3}")
    private Double temperature;

    @Value("${ai.model.max-tokens:20000}")
    private Integer maxTokens;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }

    @Bean
    public BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient() {
        return BedrockRuntimeAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }

    @Bean
    public ToolCallingChatOptions toolCallingChatOptions() {
        return ToolCallingChatOptions.builder()
                .model(modelId)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Bean
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder()
                .build();
    }

    @Bean
    public BedrockProxyChatModel bedrockConverseProxyChatModel(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
            ToolCallingChatOptions toolCallingChatOptions,
            ToolCallingManager toolCallingManager) {

        ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        return new BedrockProxyChatModel(
                bedrockRuntimeClient,
                bedrockRuntimeAsyncClient,
                toolCallingChatOptions,
                observationRegistry,
                toolCallingManager
        );
    }
}
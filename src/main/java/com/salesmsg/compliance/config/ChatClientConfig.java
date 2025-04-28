package com.salesmsg.compliance.config;


import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class ChatClientConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.secret-key}")
    private String secretKey;

    @Value("${anthropic.model-id:anthropic.claude-3-5-sonnet-20240620-v1:0}")
    private String modelId;

    @Value("${anthropic.temperature:0.7}")
    private Float temperature;

    @Value("${anthropic.max-tokens:1000}")
    private Integer maxTokens;

    @Autowired
    private VectorStore vectorStore;

    @Bean
    public BedrockProxyChatModel bedrockConverseProxyChatModel(BedrockRuntimeClient bedrockRuntimeClient) {
        BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();

        FunctionCallingOptions defaultOptions = FunctionCallingOptions.builder()
                .model(modelId)
                .temperature(0.7)
                .maxTokens(1000)
                .build();

        FunctionCallbackResolver functionCallbackResolver = new DefaultFunctionCallbackResolver();

        List<FunctionCallback> toolFunctionCallbacks = new ArrayList<>();
        ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        return new BedrockProxyChatModel(
                bedrockRuntimeClient,
                bedrockRuntimeAsyncClient,
                defaultOptions,
                functionCallbackResolver,
                toolFunctionCallbacks,
                observationRegistry
        );
    }

    @Bean
    public ChatClient chatClient(BedrockProxyChatModel chatModel) {

        ChatMemory chatMemory = new InMemoryChatMemory();
        MessageChatMemoryAdvisor chatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory);
        PromptChatMemoryAdvisor promptMemoryAdvisor = new PromptChatMemoryAdvisor(chatMemory);
        QuestionAnswerAdvisor questionAnswerAdvisor = new QuestionAnswerAdvisor(vectorStore);
        List<Advisor> advisorList = Arrays.asList(chatMemoryAdvisor, promptMemoryAdvisor, questionAnswerAdvisor);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(advisorList)
                .build();

    }

}
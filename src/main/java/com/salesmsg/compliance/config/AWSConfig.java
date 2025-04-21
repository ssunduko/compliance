package com.salesmsg.compliance.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.kendra.KendraClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configuration for AWS services used in the application.
 * Sets up clients for Bedrock (AI/ML), S3 (storage), and Kendra (search).
 */
@Configuration
@Slf4j
public class AWSConfig {

    @Value("${aws.accessKey}")
    private String accessKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.kendra.indexId}")
    private String kendraIndexId;

    /**
     * Creates a credentials provider using the configured access key and secret key.
     */
    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );
    }

    /**
     * Creates an Amazon Bedrock client for accessing foundation models.
     */
    @Bean
    public BedrockClient bedrockClient() {
        BedrockClient client = BedrockClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();

        // Validate connection by listing available models
        try {
            client.listFoundationModels(ListFoundationModelsRequest.builder().build());
            log.info("Successfully connected to AWS Bedrock service");
        } catch (Exception e) {
            log.error("Failed to connect to AWS Bedrock service", e);
        }

        return client;
    }

    /**
     * Creates an Amazon Bedrock Runtime client for running inference with foundation models.
     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    /**
     * Creates an Amazon S3 client for object storage operations.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    /**
     * Creates an Amazon Kendra client for intelligent search operations.
     */
    @Bean
    public KendraClient kendraClient() {
        return KendraClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    /**
     * @return The configured Kendra index ID for the compliance knowledge base.
     */
    @Bean
    public String kendraIndexId() {
        return kendraIndexId;
    }
}
package com.salesmsg.compliance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for Firecrawl API integration.
 */
@Configuration
public class FirecrawlConfig {

    @Value("${firecrawl.api.timeout:60}")
    private int timeoutSeconds;

    @Bean
    public RestTemplate firecrawlRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
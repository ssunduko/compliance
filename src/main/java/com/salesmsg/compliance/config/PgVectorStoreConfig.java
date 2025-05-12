package com.salesmsg.compliance.config;


import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for PostgreSQL Vector Store (PGVector) for RAG.
 */
@Configuration
public class PgVectorStoreConfig {

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.pgvector.index-type:HNSW}")
    private String indexType;

    @Value("${spring.ai.vectorstore.pgvector.distance-type:COSINE_DISTANCE}")
    private String distanceType;

    @Value("${spring.ai.document.chunk-size:1024}")
    private int chunkSize;

    @Value("${spring.ai.document.chunk-overlap:128}")
    private int chunkOverlap;

    @Bean
    public VectorStore vectorStore(DataSource dataSource,
                                   EmbeddingModel embeddingModel) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("compliance_embeddings")
                .distanceType(PgVectorStore.PgDistanceType.valueOf(distanceType))
                .indexType(PgVectorStore.PgIndexType.valueOf(indexType))
                .removeExistingVectorStoreTable(true)
                .initializeSchema(true)
                .build();
    }
}
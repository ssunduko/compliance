package com.salesmsg.compliance.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class SimpleVectorStoreConfig {

    @Bean
    public VectorStore getVectorStore(EmbeddingModel embeddingModel){

        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        Path resourcePath = Paths.get("src/test/resources/text/rules.txt");
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(resourcePath) );
        TokenTextSplitter splitter = new TokenTextSplitter();
        vectorStore.add(splitter.apply(reader.get()));

        return vectorStore;
    }
}
package com.salesmsg.compliance.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Configuration
public class InMemoryEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel inMemoryEmbeddingModel() {
        return new InMemoryEmbeddingModel(1536);
    }

    public static class InMemoryEmbeddingModel implements EmbeddingModel {
        private final int dimensions;
        private final Random random = new Random(42);

        public InMemoryEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream()
                    .map(this::embed)
                    .toList();
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            // Extract the texts from the request
            List<String> texts = request.getInstructions();

            // Generate embeddings for each text and convert to Embedding objects
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                float[] vector = this.embed(text);
                embeddings.add(new Embedding(vector, i)); // Using index i instead of the text
            }

            // Create and return the response
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(String text) {
            // Create deterministic embedding based on text hashcode
            random.setSeed(text.hashCode());

            float[] embedding = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                embedding[i] = random.nextFloat() * 2 - 1;
            }

            // Normalize to unit vector
            float magnitude = 0;
            for (float val : embedding) {
                magnitude += val * val;
            }
            magnitude = (float) Math.sqrt(magnitude);

            for (int i = 0; i < dimensions; i++) {
                embedding[i] /= magnitude;
            }

            return embedding;
        }

        @Override
        public float[] embed(Document document) {
            // Extract text content from the document
            // Typical approach is to use the document's content
            String textContent = document.getText();

            // Generate embedding for the document content
            return embed(textContent);
        }

        @Override
        public int dimensions() {
            return dimensions;
        }
    }
}

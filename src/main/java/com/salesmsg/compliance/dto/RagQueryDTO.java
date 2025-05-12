package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for RAG query results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagQueryDTO {

    private String query;
    private List<DocumentResult> results;
    private Integer count;

    /**
     * Document result from a RAG query.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentResult {
        private String content;
        private Double relevanceScore;
        private Map<String, Object> metadata;
    }
}
package com.salesmsg.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response model for Firecrawl scrape API v1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirecrawlScrapeResponse {

    private boolean success;

    private FirecrawlData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirecrawlData {

        /**
         * Markdown content of the page
         */
        private String markdown;

        /**
         * HTML version of the content on page if `html` is in `formats`
         */
        private String html;

        /**
         * Raw HTML content of the page if `rawHtml` is in `formats`
         */
        private String rawHtml;

        /**
         * Screenshot of the page if `screenshot` is in `formats`
         */
        private String screenshot;

        /**
         * List of links on the page if `links` is in `formats`
         */
        private List<String> links;

        /**
         * Metadata about the page
         */
        private Metadata metadata;

        /**
         * Displayed when using LLM Extraction. Extracted data from the page following the schema defined.
         */
        @JsonProperty("llm_extraction")
        private Map<String, Object> llmExtraction;

        /**
         * Can be displayed when using LLM Extraction. Warning message will let you know any issues with the extraction.
         */
        private String warning;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private String title;
        private String description;
        private String language;
        private String sourceURL;
        private Integer statusCode;
        private String error;

        // Additional metadata fields can be present
        @JsonProperty("og:title")
        private String ogTitle;

        @JsonProperty("og:description")
        private String ogDescription;

        @JsonProperty("og:image")
        private String ogImage;

        @JsonProperty("og:url")
        private String ogUrl;

        @JsonProperty("og:site_name")
        private String ogSiteName;

        @JsonProperty("og:type")
        private String ogType;

        @JsonProperty("twitter:card")
        private String twitterCard;

        @JsonProperty("twitter:title")
        private String twitterTitle;

        @JsonProperty("twitter:description")
        private String twitterDescription;

        @JsonProperty("robots")
        private String robots;

        @JsonProperty("keywords")
        private String keywords;
    }
}
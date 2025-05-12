package com.salesmsg.compliance.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Data loader for pre-populating the vector store with compliance knowledge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceDataLoader implements CommandLineRunner {

    private final VectorStore vectorStore;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing compliance knowledge base...");
        
        try {
            //loadCarrierGuidelines();
            loadComplianceRules();
            
            log.info("Compliance knowledge base initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing compliance knowledge base", e);
        }
    }
    
    /**
     * Load carrier guidelines into the vector store.
     */
    private void loadCarrierGuidelines() throws IOException {
        log.info("Loading carrier guidelines...");
        
        // Carrier guidelines by business type
        Map<String, String> guidelinesByType = new HashMap<>();
        
        // Retail business guidelines
        guidelinesByType.put("retail", readResourceFile("data/guidelines/retail_guidelines.txt"));
        
        // Healthcare business guidelines
        guidelinesByType.put("healthcare", readResourceFile("data/guidelines/healthcare_guidelines.txt"));
        
        // Financial business guidelines
        guidelinesByType.put("financial", readResourceFile("data/guidelines/financial_guidelines.txt"));
        
        // General business guidelines
        guidelinesByType.put("general", readResourceFile("data/guidelines/general_guidelines.txt"));
        
        // Create documents and add to vector store
        List<Document> documents = new ArrayList<>();
        TokenTextSplitter splitter = new TokenTextSplitter();
        
        for (Map.Entry<String, String> entry : guidelinesByType.entrySet()) {
            String businessType = entry.getKey();
            String content = entry.getValue();
            
            if (content == null || content.trim().isEmpty()) {
                // Use fallback content if file not found
                content = generateFallbackGuidelines(businessType);
            }
            
            // Create main document with metadata
            Document mainDoc = new Document(content, Map.of(
                    "type", "carrier_guideline",
                    "business_type", businessType,
                    "source", "system"
            ));
            
            // Split the document into chunks
            List<Document> chunks = splitter.split(mainDoc);
            documents.addAll(chunks);
        }
        
        // Store in vector store
        if (!documents.isEmpty()) {
            vectorStore.write(documents);
            log.info("Loaded {} carrier guideline documents", documents.size());
        }
    }
    
    /**
     * Load compliance rules into the vector store.
     */
    private void loadComplianceRules() throws IOException {
        log.info("Loading compliance rules...");
        
        // Read compliance rules
        String rules = readResourceFile("text/rules.txt");
        
        if (rules == null || rules.trim().isEmpty()) {
            // Use fallback content if file not found
            rules = generateFallbackRules();
        }
        
        // Create document
        Document rulesDoc = new Document(rules, Map.of(
                "type", "compliance_rules",
                "source", "system"
        ));
        
        // Split the document
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.split(rulesDoc);
        
        // Store in vector store
        if (!chunks.isEmpty()) {
            vectorStore.write(chunks);
            log.info("Loaded {} compliance rule documents", chunks.size());
        }
    }
    
    /**
     * Read content from a resource file.
     */
    private String readResourceFile(String path) {
        try {
            Resource resource = new ClassPathResource(path);
            
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            } else {
                log.warn("Resource not found: {}", path);
                return null;
            }
        } catch (IOException e) {
            log.error("Error reading resource: {}", path, e);
            return null;
        }
    }
    
    /**
     * Generate fallback carrier guidelines for a business type.
     */
    private String generateFallbackGuidelines(String businessType) {
        return "Carrier Guidelines for " + businessType + ":\n\n"
                + "1. General Requirements for All Business Types:\n"
                + "   - Clear business identification in all messages\n"
                + "   - Explicit opt-in from recipients before sending messages\n"
                + "   - Clear opt-out instructions (STOP) in messages\n"
                + "   - No prohibited content (gambling, adult content, illegal substances)\n"
                + "   - No deceptive marketing practices\n"
                + "   - Compliance with TCPA, CTIA, and carrier requirements\n\n"
                + "2. Specific Guidelines for " + businessType + ":\n"
                + "   - Clearly state the purpose of each message\n"
                + "   - Include business name in each message\n"
                + "   - Provide value in each message (information, alerts, confirmations)\n"
                + "   - Respect message frequency expectations\n"
                + "   - Maintain accurate opt-in records\n"
                + "   - Honor opt-out requests immediately\n\n"
                + "3. Best Practices:\n"
                + "   - Keep messages concise and to the point\n"
                + "   - Send messages during appropriate hours\n"
                + "   - Limit use of abbreviations and slang\n"
                + "   - Provide a way for customers to get more information\n"
                + "   - Test message delivery before full deployment\n";
    }
    
    /**
     * Generate fallback compliance rules.
     */
    private String generateFallbackRules() {
        return "10DLC Compliance Rules:\n\n"
                + "1. Registration Requirements:\n"
                + "   - Register your brand with the Campaign Registry\n"
                + "   - Register each messaging campaign separately\n"
                + "   - Provide accurate information about your company and use cases\n"
                + "   - Use cases must match actual messaging content\n\n"
                + "2. Opt-In Requirements:\n"
                + "   - Explicit opt-in required before sending messages\n"
                + "   - Clear disclosure of message frequency and purpose\n"
                + "   - Maintain records of opt-in consent\n"
                + "   - Separate opt-in for each messaging program\n\n"
                + "3. Message Content Requirements:\n"
                + "   - Include business name in each message\n"
                + "   - Include opt-out instructions in each message\n"
                + "   - Content must match registered use case\n"
                + "   - No prohibited content (e.g., illegal activities, adult content)\n\n"
                + "4. Opt-Out Handling:\n"
                + "   - Honor all opt-out requests immediately\n"
                + "   - Support standard opt-out keywords (STOP, CANCEL, END, QUIT, UNSUBSCRIBE)\n"
                + "   - Confirm opt-out requests\n"
                + "   - No marketing messages after opt-out\n\n";
    }
}
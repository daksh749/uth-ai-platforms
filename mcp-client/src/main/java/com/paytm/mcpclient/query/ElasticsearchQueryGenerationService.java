package com.paytm.mcpclient.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.mcpclient.elasticsearch.service.EsQueryRulesService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Advanced LLM-powered Elasticsearch query generation service.
 * This service uses the LLM with schema awareness and business rules to generate 
 * intelligent, optimized Elasticsearch queries from natural language prompts.
 * 
 * Key Features:
 * - Schema-aware query generation
 * - Nested field detection and handling
 * - Proper date format handling (dd/MM/yyyy in queries, epoch for execution)
 * - Field type-based query strategy selection
 * - Business rule compliance
 */
@Service
@Slf4j
public class ElasticsearchQueryGenerationService {

    private final ChatLanguageModel chatLanguageModel;
    private final EsQueryRulesService rulesService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchQueryGenerationService(ChatLanguageModel chatLanguageModel, EsQueryRulesService rulesService) {
        this.chatLanguageModel = chatLanguageModel;
        this.rulesService = rulesService;
    }

    /**
     * Generate an Elasticsearch query from user prompt, schema, and host information.
     * This is the main entry point for LLM-powered query generation.
     * 
     * @param userPrompt The natural language user request
     * @param esSchema The Elasticsearch schema/mapping
     * @return Generated Elasticsearch query as JSON string
     */
    public String generateQuery(String userPrompt, Object esSchema) {
        log.info("Generating Elasticsearch query for prompt: {}", userPrompt);
        
        long startTime = System.currentTimeMillis();
        
        try {
            String llmPrompt = buildQueryGenerationPrompt(userPrompt, esSchema);
            String llmResponse = chatLanguageModel.generate(llmPrompt);
            String cleanedQuery = cleanAndValidateQuery(llmResponse);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Query generation completed in {}ms", executionTime);
            
            return cleanedQuery;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Query generation failed after {}ms for prompt: {}", executionTime, userPrompt, e);
            
            return getFallbackQuery();
        }
    }

    /**
     * Build dynamic LLM prompt with actual schema and rules
     */
    private String buildQueryGenerationPrompt(String userPrompt, Object esSchema) {
        try {
            // Get the actual schema JSON
            String schemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(esSchema);
            
            // Get only essential rules to keep prompt focused
            Map<String, Object> allRules = rulesService.getRulesMap();
            Map<String, Object> essentialRules = extractEssentialRules(allRules);
            String rulesJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(essentialRules);
            
            return String.format("""
                Generate Elasticsearch query for: "%s"
                
                Schema: %s
                
                Rules: %s
                
                CRITICAL DATE FORMAT REQUIREMENT:
                - Use dd/MM/yyyy format for ALL dates in the query JSON
                - Use EXACT dates provided by user: "2nd Jan 2025" = "02/01/2025"
                - Do NOT round to month start: "2nd Jan" stays "02/01", not "01/01"
                - Never use ISO format (yyyy-MM-ddTHH:mm:ss.sssZ)
                
                RESPOND WITH ONLY JSON - NO TEXT BEFORE OR AFTER
                Format: {"query":{"bool":...}}
                """, userPrompt, schemaJson, rulesJson);
                
        } catch (Exception e) {
            log.error("Failed to build query generation prompt", e);
            throw new RuntimeException("Failed to build LLM prompt for query generation", e);
        }
    }

    /**
     * Extract only the essential rules needed for query generation
     * Keep it minimal to focus LLM on user request and schema analysis
     */
    private Map<String, Object> extractEssentialRules(Map<String, Object> allRules) {
        // Return the simplified rules directly since we've already simplified the rules file
        return allRules;
    }

    /**
     * Clean and validate the LLM response to ensure it's a proper Elasticsearch query
     */
    private String cleanAndValidateQuery(String llmResponse) {
        try {
            // Remove markdown code blocks and extra text
            String cleaned = llmResponse.trim();
            
            // Remove markdown code blocks
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            
            // Find the JSON object boundaries
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1);
            }
            
            // Validate it's proper JSON
            JsonNode queryNode = objectMapper.readTree(cleaned);
            
            // Ensure it has a query structure
            if (!queryNode.has("query")) {
                log.warn("Generated query missing 'query' field, wrapping in query structure");
                Map<String, Object> wrappedQuery = Map.of("query", objectMapper.readValue(cleaned, Map.class));
                return objectMapper.writeValueAsString(wrappedQuery);
            }
            
            // Return the validated, pretty-printed query
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(queryNode);
            
        } catch (Exception e) {
            log.error("Failed to clean and validate LLM query response: {}", llmResponse, e);
            throw new RuntimeException("Invalid query generated by LLM", e);
        }
    }

    /**
     * Get a fallback query when generation fails
     */
    private String getFallbackQuery() {
        Map<String, Object> fallbackQuery = Map.of(
            "query", Map.of("match_all", Map.of()),
            "_source", false,
            "size", 10
        );
        
        try {
            return objectMapper.writeValueAsString(fallbackQuery);
        } catch (Exception e) {
            return "{\"query\":{\"match_all\":{}}}";
        }
    }

    /**
     * Validate if a query string is valid Elasticsearch JSON
     */
    public boolean isValidElasticsearchQuery(String queryJson) {
        try {
            JsonNode queryNode = objectMapper.readTree(queryJson);
            return queryNode.has("query") || queryNode.has("aggs") || queryNode.has("sort");
        } catch (Exception e) {
            return false;
        }
    }

}

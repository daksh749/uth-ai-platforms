package com.paytm.mcpclient.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.mcpclient.elasticsearch.service.EsQueryRulesService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
     * @param esHost The target Elasticsearch host
     * @param extractedParams Optional pre-extracted parameters (can be null)
     * @return Generated Elasticsearch query as JSON string
     */
    public String generateQuery(String userPrompt, Object esSchema, String esHost, Map<String, Object> extractedParams) {
        log.info("Generating Elasticsearch query for prompt: {}", userPrompt);
        
        long startTime = System.currentTimeMillis();
        
        try {
            String llmPrompt = buildQueryGenerationPrompt(userPrompt, esSchema, esHost, extractedParams);
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
    private String buildQueryGenerationPrompt(String userPrompt, Object esSchema, String esHost, Map<String, Object> extractedParams) {
        try {
            // Get the actual schema JSON
            String schemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(esSchema);
            
            // Get relevant rules from the rules service
            Map<String, Object> allRules = rulesService.getRulesMap();
            String rulesJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allRules);
            
            return String.format("""
                You are an expert Elasticsearch query generator. Analyze the schema and generate a query.

                USER REQUEST: "%s"

                ELASTICSEARCH SCHEMA:
                %s

                QUERY GENERATION RULES:
                %s

                INSTRUCTIONS:
                1. Analyze the esSchema carefully to find relevant fields for the user request
                2. Use the rules to determine proper and exact query structure
                3. For dates: use dd/MM/yyyy format in the query (will be converted to epoch later)
                4. For nested fields: wrap in nested query with correct path
                5. For keyword fields: use term/terms query
                6. For text fields: use match query
                7. If you don't find any date in user-prompt then put (gte:NOW, lte:NOW)
                8. Return ONLY valid Elasticsearch query JSON

                RESPONSE FORMAT:
                {"query":{"bool": ...}}

                Generate the exact elasticsearch query, don;t give me any example:
                """, userPrompt, schemaJson, rulesJson);
                
        } catch (Exception e) {
            log.error("Failed to build query generation prompt", e);
            throw new RuntimeException("Failed to build LLM prompt for query generation", e);
        }
    }

    /**
     * Extract only the relevant rules needed for query generation
     */
    private Map<String, Object> extractRelevantRules(Map<String, Object> allRules) {
        Map<String, Object> relevantRules = new HashMap<>();
        
        // Add only the most important rules for query generation
        if (allRules.containsKey("QUERY_TYPE_DETERMINATION_RULES")) {
            relevantRules.put("QUERY_TYPE_DETERMINATION_RULES", allRules.get("QUERY_TYPE_DETERMINATION_RULES"));
        }
        
        if (allRules.containsKey("NESTED_QUERY_RULES")) {
            relevantRules.put("NESTED_QUERY_RULES", allRules.get("NESTED_QUERY_RULES"));
        }
        
        if (allRules.containsKey("DATE_HANDLING_BUSINESS_RULES")) {
            relevantRules.put("DATE_HANDLING_BUSINESS_RULES", allRules.get("DATE_HANDLING_BUSINESS_RULES"));
        }
        
        if (allRules.containsKey("QUERY_COMBINATION_RULES")) {
            relevantRules.put("QUERY_COMBINATION_RULES", allRules.get("QUERY_COMBINATION_RULES"));
        }
        
        return relevantRules;
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

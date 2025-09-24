package com.paytm.mcpclient.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.mcpclient.elasticsearch.service.EsQueryRulesService;
import com.paytm.mcpclient.mcp.service.McpClientService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Enhanced LLM service with comprehensive rules-based query generation
 * Focuses on the main ChatTestController flow with intelligent ES query orchestration
 */
@Slf4j
@Service
public class LlmService {
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private McpClientService mcpClientService;
    
    @Autowired
    private EsQueryRulesService rulesService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Generate a response from the LLM for a given prompt
     */
    public String generateResponse(String prompt) {
        try {
            log.debug("Sending prompt to LLM: {}", prompt);
            String response = chatLanguageModel.generate(prompt);
            log.debug("LLM response: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error generating LLM response", e);
            throw new RuntimeException("Failed to generate LLM response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute simple search flow: Schema → LLM → Query → Host → Search
     * Now uses enhanced rules-based prompt generation
     */
    public Object generateQueryWithFlow(String userPrompt, String esSchema) {
        log.info("Starting enhanced search flow with rules for prompt: {}", userPrompt);
        
        try {
            // Use the new enhanced flow prompt that includes rules
            String flowPrompt = buildEnhancedFlowPrompt(userPrompt, esSchema);
            return executeFlowWithTools(flowPrompt);
        } catch (Exception e) {
            log.error("Enhanced flow execution failed for prompt: {}", userPrompt, e);
            throw new RuntimeException("Enhanced flow execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build enhanced flow prompt that includes rules and tool orchestration
     * This replaces buildSimpleFlowPrompt with rules-based intelligence
     */
    private String buildEnhancedFlowPrompt(String userPrompt, String esSchema) {
        // Get rules from rules service
        String queryRules = rulesService.getQueryRulesAsJson();
        
        // Check if rules are available
        if (!rulesService.areRulesAvailable()) {
            log.warn("Query rules not available for flow, using basic flow prompt");
            return buildBasicFlowPrompt(userPrompt, esSchema);
        }
        
        // Log rules metadata for debugging
        Map<String, Object> metadata = rulesService.getRulesMetadata();
        log.debug("Using enhanced flow with rules version: {}", metadata.get("version"));
        
        long currentTimeMillis = System.currentTimeMillis();
        long thirtyDaysAgoMillis = currentTimeMillis - (30L * 24 * 60 * 60 * 1000);
        
        return String.format("""
            You are an intelligent Elasticsearch assistant with comprehensive query generation rules. Execute this exact flow:
            
            USER PROMPT: "%s"
            
            ELASTICSEARCH SCHEMA:
            %s
            
            QUERY GENERATION RULES:
            %s
            
            CURRENT TIME CONTEXT:
            - Current timestamp (epoch millis): %d
            - 30 days ago (epoch millis): %d
            
            EXECUTION FLOW - Follow these steps exactly:
            
            STEP 1: ANALYZE USER INTENT
            - Use field_mapping_rules to identify which fields to search (e.g., RRN → searchFields.searchReferenceId + participants.bankData.rrn)
            - Use date_handling_rules for temporal queries (single date = range to now, two dates = bounded range)
            - Use field_value_rules for exact value matching (status "2" stays "2", not "success")
            
            STEP 2: GENERATE ELASTICSEARCH QUERY
            - Follow query_structure_rules for nested queries (participants.* fields need nested structure)
            - Use error_prevention rules to avoid common mistakes (no dateContext field, exact field names)
            - Reference examples section for similar query patterns
            
            STEP 3: DETERMINE OPTIMAL HOST AND INDICES
            - For recent data (today, yesterday, this week): use PRIMARY host
            - For historical data (specific dates, months, years): use TERTIARY host  
            - For backup scenarios: use SECONDARY host
            - Index pattern: payment-history-MM-YYYY* (e.g., payment-history-09-2025* for Sept 2025)
            
            STEP 4: EXECUTE SEARCH TOOLS
            Available tools:
            1. es_host_search(startDate, endDate) - Find optimal host for date range
            2. es_search(query, selectedHost, indices) - Execute search with specific parameters
            
            STEP 5: RETURN RESULTS
            Execute the tools and return the search results.
            
            CRITICAL REQUIREMENTS:
            - Follow ALL rules from the QUERY GENERATION RULES section
            - Use exact field names from the schema
            - Convert dates to epoch milliseconds
            - Use nested queries for participants.* fields
            - Combine conditions with bool/must queries
            - Never add fields not in the schema (like dateContext)
            
            TOOL EXECUTION ORDER:
            1. First call es_host_search with date range if date-based query
            2. Then call es_search with the generated query, selected host, and indices
            
            Start execution now.
            """, userPrompt, esSchema, queryRules, currentTimeMillis, thirtyDaysAgoMillis);
    }
    
    /**
     * Fallback basic flow prompt when rules are not available
     */
    private String buildBasicFlowPrompt(String userPrompt, String esSchema) {
        long currentTimeMillis = System.currentTimeMillis();
        long thirtyDaysAgoMillis = currentTimeMillis - (30L * 24 * 60 * 60 * 1000);
        
        return String.format("""
            You are an intelligent Elasticsearch assistant. Execute this exact flow:
            
            User Prompt: "%s"
            
            Elasticsearch Schema: %s
            
            BASIC RULES:
            - Use exact field names from the schema
            - For status field, use exact values (don't interpret "2" as "success")
            - For date ranges, use epoch milliseconds in range queries
            - For nested fields like participants.*, use nested query structure
            - Combine multiple conditions with bool/must queries
            - Current timestamp: %d
            - 30 days ago: %d
            
            Execute the search tools and return results.
            """, userPrompt, esSchema, currentTimeMillis, thirtyDaysAgoMillis);
    }
    
    /**
     * Execute flow with tools orchestration
     */
    private Object executeFlowWithTools(String flowPrompt) {
        try {
            log.debug("Executing flow with enhanced rules-based prompt");
            
            // Generate LLM response with tool orchestration
            String llmResponse = chatLanguageModel.generate(flowPrompt);
            log.debug("LLM flow response received");
            
            // Parse and execute the LLM's tool calls
            return parseQueryResponse(llmResponse);
            
        } catch (Exception e) {
            log.error("Tool execution failed", e);
            throw new RuntimeException("Flow execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse LLM response and extract query components
     */
    private Object parseQueryResponse(String llmResponse) {
        try {
            log.debug("Parsing LLM response for tool execution");
            
            // Try to parse as JSON first
            JsonNode responseNode = objectMapper.readTree(llmResponse);
            
            // Extract query data
            @SuppressWarnings("unchecked")
            Map<String, Object> queryData = objectMapper.convertValue(responseNode, Map.class);
            
            // Determine host selection
            String selectedHost = determineHostFromResponse(queryData);
            
            // Execute the search
            Object queryObject = queryData.get("query");
            String index = (String) queryData.get("index");
            List<String> indices = List.of(index);

            // Convert query object to JSON string for MCP call
            String queryJson;
            try {
                queryJson = objectMapper.writeValueAsString(queryObject);
                log.info("Serialized query object to JSON: {}", queryJson);
            } catch (Exception e) {
                log.error("Failed to serialize query object: {}", queryObject, e);
                queryJson = "{\"match_all\":{}}"; // fallback
            }

            log.info("Executing es_search with host: {}, index: {}, query: {}", selectedHost, index, queryJson);
            return mcpClientService.searchElasticsearch(queryJson, selectedHost, indices);

        } catch (Exception e) {
            log.error("Failed to parse query response, treating as direct tool execution", e);
            
            // Fallback: treat response as direct tool execution result
            return llmResponse;
        }
    }
    
    /**
     * Determine host from LLM response
     */
    private String determineHostFromResponse(Map<String, Object> queryData) {
        // Try to get host recommendation from LLM response
        String recommendedHost = (String) queryData.get("recommendedHost");
        if (recommendedHost != null) {
            return recommendedHost;
        }
        
        // Default to PRIMARY
        return "PRIMARY";
    }
    
    /**
     * Test LLM connection
     */
    public Boolean testConnection() {
        try {
            log.debug("Testing LLM connection");
            String testResponse = chatLanguageModel.generate("Test connection. Respond with 'OK'");
            boolean isConnected = testResponse != null && !testResponse.trim().isEmpty();
            log.debug("LLM connection test result: {}", isConnected);
            return isConnected;
        } catch (Exception e) {
            log.error("LLM connection test failed", e);
            return false;
        }
    }
}


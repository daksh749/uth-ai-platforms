package com.paytm.mcpserver.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Service for building Elasticsearch queries using LLM (Ollama Llama3.2)
 *
 * This service takes natural language prompts and schema context (from es_schema tool)
 * and converts them to Elasticsearch Query DSL using LLM intelligence.
 *
 * NO DIRECT SCHEMA DEPENDENCY - Schema context provided by LLM orchestration
 */
@Service
@Log4j2
public class ElasticsearchQueryBuilderService {

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.ollama.chat.options.model}")
    private String ollamaModel;
    
    @Value("${ollama.temperature:0.1}")
    private double ollamaTemperature;
    
    @Value("${ollama.top-p:0.9}")
    private double ollamaTopP;
    
    @Value("${ollama.max-tokens:4000}")
    private int ollamaMaxTokens;
    
    @Value("${ollama.context-length:8192}")
    private int ollamaContextLength;
    
    @Value("${ollama.num-ctx:8192}")
    private int ollamaNumCtx;
    
    @Value("${ollama.timeout:120000}")
    private long ollamaTimeout;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ElasticsearchQueryBuilderService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        // Configure timeout specifically for Ollama calls
        configureOllamaTimeout();
    }
    
    /**
     * Configure RestTemplate with Ollama-specific timeout
     */
    private void configureOllamaTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) ollamaTimeout);
        factory.setReadTimeout((int) ollamaTimeout);
        this.restTemplate.setRequestFactory(factory);
    }

    /**
     * Build Elasticsearch query from natural language prompt with schema context
     *
     * @param prompt Natural language query
     * @param schemaContext Elasticsearch schema (from es_schema tool)
     * @param maxResults Maximum number of results
     * @param includeAggregations Whether to include aggregations
     * @param sortBy Sort field and order (defaults to txnDate:desc if not provided)
     * @return Elasticsearch Query DSL JSON
     */
    public String buildQueryFromPrompt(String prompt, String schemaContext, Integer maxResults,
                                       Boolean includeAggregations, String sortBy) {
        try {
            // Validate inputs
            if (prompt == null || prompt.trim().isEmpty()) {
                throw new IllegalArgumentException("Prompt cannot be empty");
            }

            if (schemaContext == null || schemaContext.trim().isEmpty()) {
                throw new IllegalArgumentException("Schema context is required - call es_schema tool first");
            }

            // Build LLM prompt with provided schema context and user prompt
            String systemPrompt = buildSystemPrompt(schemaContext, prompt);
            
            // Call Ollama LLM
            String llmResponse = callOllamaLLM(systemPrompt);

            // Extract and validate JSON
            String queryJson = extractJsonFromResponse(llmResponse);

            // Validate and enhance query
            String enhancedQuery = validateAndEnhanceQuery(queryJson, maxResults, includeAggregations, sortBy);
            
            // Process date fields and convert to epoch millis
            return processDateFields(enhancedQuery);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build ES query: " + e.getMessage(), e);
        }
    }

    /**
     * Call Ollama LLM API
     */
    private String callOllamaLLM(String systemPrompt) {
        try {
            String ollamaUrl = ollamaBaseUrl + "/api/generate";
            
            Map<String, Object> request = Map.of(
                    "model", ollamaModel,
                    "prompt", systemPrompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", ollamaTemperature,
                            "top_p", ollamaTopP,
                            "num_predict", ollamaMaxTokens,
                            "num_ctx", ollamaNumCtx
                    )
            );
            log.info("prompt in the request is : {}", request.get("prompt"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                ollamaUrl, 
                HttpMethod.POST, 
                entity, 
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getBody() != null && response.getBody().containsKey("response")) {
                return (String) response.getBody().get("response");
            }

            throw new RuntimeException("Invalid response from Ollama");

        } catch (Exception e) {
            throw new RuntimeException("Failed to call Ollama LLM: " + e.getMessage(), e);
        }
    }

    /**
     * Build comprehensive system prompt with provided schema context
     */
    private String buildSystemPrompt(String schemaContext, String userPrompt) {
        return String.format("""
                Generate Elasticsearch Query DSL from user input.
                
                USER INPUT / USER PROMPT:
                %s
                
                Follow the below rules strictly:
                
                R1: Always first analyze the user prompt to understand what user is asking.
                R2: As per your knowledge,
                    - extract the necessary information from user prompt 
                    - iterate over all the fields in esSchema provided
                    - match the info extracted with the "description" or "aliases" of each field as given in esSchema format
                    - if a match is found then take that "fieldName" from schema into account
                    - example : if user asks "I need an elasticsearch query for user id 75284"
                                - then extract the info -> user id 75284
                                - in esSchema "user id" field in aliases maps to fieldName "entityId"
                                - so make a query on entityId as per the clause and queryType specified in the schema for that fieldName.
                R2: Follow the elasticsearch-schema/schema to make the query
                R3: Match the user prompt with appropriate fields in es-schema.
                R4: Each field in schema has a specific format:
                    - fieldName: the name of field on which query is built
                    - type: keyword/text/etc.. what type of field it is
                    - queryType: if that field supports term/terms/range/etc.. queries
                    - clause: if query on that field should be done in filter/must/etc. clause
                    - description: what the field is for
                    - example: consider the example query on that field for reference
                R5: After analyzing the user-prompt, these rules and elastic-search schema, make a valid elastic-search query based on that and give me the exact query.
                
                DATE RANGE RULES
                CASE1: User provides two dates in the user prompt
                       1. make a date range query with smaller/before date as "gte" and larger/after as "lte"
                       2. write the dates as given STRICTLY is in DD/MM/YYYY format
                
                CASE2: User gives a single date in user prompt
                       1. make a date range query with the single date provides as "gte" in DD/MM/YYYY format STRICTLY
                       2. for "lte" write the word "NOW" in lte
                       
                CASE3: User doesn't provide any date in user prompt
                       1. make a date range query with "gte" as "NOW" and "lte" as "NOW" STRICTLY don't write anything else
                
                FOLLOW THE DATE RANGE RULES STRICTLY, AND IN EVERY QUERY WHETHER DATE IS GIVEN BY USER OR NOT, YOU NEED TO MAKE THE DATE RANGE QUERY AS PER ABOVE RULES.
                
                ELASTICSEARCH SCHEMA:
                %s
            
                Return ONLY the JSON query in markdown format.
                """, userPrompt, schemaContext);
    }

    /**
     * Extract JSON from LLM response
     */
    private String extractJsonFromResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            throw new RuntimeException("Empty response from LLM");
        }

        String cleaned = llmResponse.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        // Remove any leading/trailing text before/after JSON
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1).trim();
        }

        // If no braces found, try to clean and return
        cleaned = cleaned.replaceAll("^[^{]*", "").replaceAll("[^}]*$", "");

        if (cleaned.isEmpty()) {
            throw new RuntimeException("No valid JSON found in LLM response: " + llmResponse);
        }

        return cleaned.trim();
    }

    /**
     * Validate and enhance the generated query
     */
    private String validateAndEnhanceQuery(String queryJson, Integer maxResults, Boolean includeAggregations, String sortBy) {
        try {
            // Parse JSON to validate
            JsonNode queryNode = objectMapper.readTree(queryJson);
            ObjectNode enhanced = (ObjectNode) queryNode;

            // Clean up invalid filters (like entityId: "user id")
            cleanInvalidFilters(enhanced);

            // Ensure size is set
            if (!enhanced.has("size")) {
                enhanced.put("size", maxResults != null ? maxResults : 10);
            }

            // Add sorting - default to txnDate:desc if not specified, or fix incorrect sort format
            if (!enhanced.has("sort")) {
                String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy : "txnDate:desc";
                addSortToQuery(enhanced, effectiveSortBy);
            } else {
                // Fix incorrect sort format if present (e.g., "dir": "desc" should be just "desc")
                fixSortFormat(enhanced);
            }

            // Add aggregations if requested and not present
            if (includeAggregations != null && includeAggregations && !enhanced.has("aggs")) {
                addAggregationsToQuery(enhanced);
            }

            return objectMapper.writeValueAsString(enhanced);

        } catch (Exception e) {
            // If validation fails, try basic string enhancements
            return addBasicEnhancements(queryJson, maxResults, sortBy);
        }
    }

    /**
     * Clean up invalid filters from the query
     */
    private void cleanInvalidFilters(ObjectNode queryNode) {
        try {
            JsonNode queryBoolNode = queryNode.path("query").path("bool");
            
            // Clean filter array
            if (queryBoolNode.has("filter")) {
                ArrayNode filterArray = (ArrayNode) queryBoolNode.get("filter");
                ArrayNode cleanedFilters = objectMapper.createArrayNode();
                
                for (int i = 0; i < filterArray.size(); i++) {
                    JsonNode filterNode = filterArray.get(i);
                    
                    // Check for term filters with placeholder values
                    if (filterNode.has("term")) {
                        JsonNode termNode = filterNode.get("term");
                        boolean isValid = true;
                        
                        // Check each field in the term
                        termNode.fieldNames().forEachRemaining(fieldName -> {
                            String value = termNode.get(fieldName).asText();
                            // Skip if value is a placeholder like "user id", "userId", etc.
                            if (value.equalsIgnoreCase("user id") || 
                                value.equalsIgnoreCase("userId") || 
                                value.equalsIgnoreCase("customerId") ||
                                value.equalsIgnoreCase("entityId")) {
                                // Don't add this filter
                                return;
                            }
                        });
                        
                        // Only add if all values are valid
                        if (isValid && !isPlaceholderValue(termNode)) {
                            cleanedFilters.add(filterNode);
                        }
                    } else {
                        // Keep non-term filters (like range)
                        cleanedFilters.add(filterNode);
                    }
                }
                
                ((ObjectNode) queryBoolNode).set("filter", cleanedFilters);
            }
            
            // Remove must array if it contains size or is invalid
            if (queryBoolNode.has("must")) {
                JsonNode mustNode = queryBoolNode.get("must");
                if (mustNode.isArray() && mustNode.size() > 0) {
                    ArrayNode mustArray = (ArrayNode) mustNode;
                    ArrayNode cleanedMust = objectMapper.createArrayNode();
                    
                    for (int i = 0; i < mustArray.size(); i++) {
                        JsonNode item = mustArray.get(i);
                        // Skip if it has "size" field (size should be top-level)
                        if (!item.has("size")) {
                            cleanedMust.add(item);
                        }
                    }
                    
                    // If must array is now empty, remove it
                    if (cleanedMust.size() == 0) {
                        ((ObjectNode) queryBoolNode).remove("must");
                    } else {
                        ((ObjectNode) queryBoolNode).set("must", cleanedMust);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to clean invalid filters: {}", e.getMessage());
        }
    }
    
    /**
     * Check if a term node has placeholder values
     */
    private boolean isPlaceholderValue(JsonNode termNode) {
        for (var it = termNode.fields(); it.hasNext(); ) {
            var entry = it.next();
            String value = entry.getValue().asText().toLowerCase();
            if (value.contains("user id") || value.equals("userid") || 
                value.equals("customerid") || value.equals("entityid")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add sort to query
     */
    private void addSortToQuery(ObjectNode queryNode, String sortBy) {
        try {
            String[] sortParts = sortBy.split(":");
            String field = sortParts[0].trim();
            String order = sortParts.length > 1 ? sortParts[1].trim() : "desc";

            ObjectNode sortNode = objectMapper.createObjectNode();
            sortNode.put(field, order);
            queryNode.set("sort", objectMapper.createArrayNode().add(sortNode));

        } catch (Exception e) {
            // Ignore sort addition if it fails
        }
    }

    /**
     * Fix incorrect sort format in the query
     */
    private void fixSortFormat(ObjectNode queryNode) {
        try {
            JsonNode sortNode = queryNode.get("sort");
            if (sortNode != null && sortNode.isArray() && sortNode.size() > 0) {
                JsonNode firstSort = sortNode.get(0);
                if (firstSort.isObject()) {
                    // Check if any field has incorrect "dir" format
                    ObjectNode firstSortObj = (ObjectNode) firstSort;
                    firstSortObj.fieldNames().forEachRemaining(fieldName -> {
                        JsonNode fieldValue = firstSortObj.get(fieldName);
                        if (fieldValue.isObject() && fieldValue.has("dir")) {
                            // Fix incorrect format: {"txnDate": {"dir": "desc"}} -> {"txnDate": "desc"}
                            String direction = fieldValue.get("dir").asText();
                            firstSortObj.put(fieldName, direction);
                        }
                    });
                }
            }
        } catch (Exception e) {
            // Ignore sort fixing if it fails
        }
    }

    /**
     * Process date fields and convert DD-MM-YYYY to epoch millis
     */
    private String processDateFields(String queryJson) {
        try {
            JsonNode queryNode = objectMapper.readTree(queryJson);
            ObjectNode enhanced = (ObjectNode) queryNode;
            
            // Navigate to filter array in bool query
            JsonNode queryBoolNode = enhanced.path("query").path("bool");
            if (queryBoolNode.has("filter")) {
                ArrayNode filterArray = (ArrayNode) queryBoolNode.get("filter");
                
                for (int i = 0; i < filterArray.size(); i++) {
                    JsonNode filterNode = filterArray.get(i);
                    if (filterNode.has("range") && filterNode.get("range").has("txnDate")) {
                        ObjectNode rangeNode = (ObjectNode) filterNode.get("range").get("txnDate");
                        
                        // Process gte (start date)
                        if (rangeNode.has("gte")) {
                            String gteValue = rangeNode.get("gte").asText();
                            if ("START_OF_MONTH".equals(gteValue)) {
                                gteValue = getStartOfMonthDate();
                            }
                            long gteEpoch = convertDateToEpochMillis(gteValue, true);
                            rangeNode.put("gte", gteEpoch);
                        }
                        
                        // Process lte (end date)
                        if (rangeNode.has("lte")) {
                            String lteValue = rangeNode.get("lte").asText();
                            if ("NOW_DATE".equals(lteValue)) {
                                lteValue = getCurrentDate();
                            }
                            long lteEpoch = convertDateToEpochMillis(lteValue, false);
                            rangeNode.put("lte", lteEpoch);
                        }
                    }
                }
            }
            
            return objectMapper.writeValueAsString(enhanced);
            
        } catch (Exception e) {
            log.error("Failed to process date fields: {}", e.getMessage());
            return queryJson; // Return original if processing fails
        }
    }

    /**
     * Convert DD-MM-YYYY to epoch milliseconds
     * @param dateStr Date string in DD-MM-YYYY format
     * @param startOfDay If true, use 00:00:00; if false, use 23:59:59
     */
    private long convertDateToEpochMillis(String dateStr, boolean startOfDay) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate date = LocalDate.parse(dateStr, formatter);
            ZoneId istZone = ZoneId.of("Asia/Kolkata");
            
            if (startOfDay) {
                return date.atStartOfDay(istZone).toInstant().toEpochMilli();
            } else {
                return date.atTime(23, 59, 59).atZone(istZone).toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            log.error("Failed to convert date {}: {}", dateStr, e.getMessage());
            return System.currentTimeMillis();
        }
    }

    /**
     * Get start of current month in DD-MM-YYYY format
     */
    private String getStartOfMonthDate() {
        LocalDate firstDay = LocalDate.now(ZoneId.of("Asia/Kolkata")).withDayOfMonth(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return firstDay.format(formatter);
    }

    /**
     * Get current date in DD-MM-YYYY format
     */
    private String getCurrentDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return today.format(formatter);
    }

    /**
     * Add aggregations to query
     */
    private void addAggregationsToQuery(ObjectNode queryNode) {
        try {
            ObjectNode aggsNode = objectMapper.createObjectNode();

            // Daily transaction counts
            ObjectNode dailyCountsAgg = objectMapper.createObjectNode();
            ObjectNode dateHistogram = objectMapper.createObjectNode();
            dateHistogram.put("field", "txnDate");
            dateHistogram.put("calendar_interval", "day");
            dailyCountsAgg.set("date_histogram", dateHistogram);
            aggsNode.set("daily_transaction_counts", dailyCountsAgg);

            // Status breakdown
            ObjectNode statusAgg = objectMapper.createObjectNode();
            ObjectNode statusTerms = objectMapper.createObjectNode();
            statusTerms.put("field", "status");
            statusTerms.put("size", 10);
            statusAgg.set("terms", statusTerms);
            aggsNode.set("status_breakdown", statusAgg);

            // Amount statistics
            ObjectNode amountStatsAgg = objectMapper.createObjectNode();
            ObjectNode amountStats = objectMapper.createObjectNode();
            amountStats.put("field", "amount");
            amountStatsAgg.set("stats", amountStats);
            aggsNode.set("amount_statistics", amountStatsAgg);

            queryNode.set("aggs", aggsNode);

        } catch (Exception e) {
            // Ignore aggregation addition if it fails
        }
    }

    /**
     * Add basic enhancements if JSON parsing fails
     */
    private String addBasicEnhancements(String queryJson, Integer maxResults, String sortBy) {
        try {
            // Simple string manipulation fallback
            if (!queryJson.contains("\"size\"")) {
                int lastBrace = queryJson.lastIndexOf('}');
                if (lastBrace > 0) {
                    String sizeField = String.format(",\"size\":%d", maxResults != null ? maxResults : 10);
                    queryJson = queryJson.substring(0, lastBrace) + sizeField + queryJson.substring(lastBrace);
                }
            }

            // Fix incorrect sort format or add default sorting
            if (queryJson.contains("\"dir\"")) {
                // Fix incorrect sort format: {"txnDate": {"dir": "desc"}} -> {"txnDate": "desc"}
                queryJson = queryJson.replaceAll("\"(\\w+)\"\\s*:\\s*\\{\\s*\"dir\"\\s*:\\s*\"(\\w+)\"\\s*\\}", "\"$1\":\"$2\"");
            }
            
            if (!queryJson.contains("\"sort\"")) {
                String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy : "txnDate:desc";
                String[] sortParts = effectiveSortBy.split(":");
                String field = sortParts[0].trim();
                String order = sortParts.length > 1 ? sortParts[1].trim() : "desc";
                
                int lastBrace = queryJson.lastIndexOf('}');
                if (lastBrace > 0) {
                    String sortField = String.format(",\"sort\":[{\"%s\":\"%s\"}]", field, order);
                    queryJson = queryJson.substring(0, lastBrace) + sortField + queryJson.substring(lastBrace);
                }
            }

            return queryJson;

        } catch (Exception e) {
            return queryJson; // Return as-is if all enhancements fail
        }
    }
}
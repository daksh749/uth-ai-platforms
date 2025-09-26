package com.paytm.mcpclient.strategy;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.UserIntent;
import com.paytm.mcpclient.mcp.service.McpClientService;
import com.paytm.mcpclient.elasticsearch.ElasticsearchQueryService;
import com.paytm.mcpclient.query.ElasticsearchQueryGenerationService;
import com.paytm.mcpclient.util.DateQueryProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Strategy for handling COMPLEX_SEARCH intent.
 * This is the most sophisticated strategy that executes the full workflow:
 * 1. Get Elasticsearch schema
 * 2. Extract parameters and generate query
 * 3. Find optimal host based on date range
 * 4. Execute search with generated query
 * 
 * Examples of user prompts that trigger this strategy:
 * - "transactions between 1st Sept to 5th Sept with status 2"
 * - "find payments above 1000 rupees"
 * - "show me failed transactions"
 * - "get data where status is completed"
 */
@Component
@Slf4j
public class ComplexSearchStrategy implements IntentExecutionStrategy {

    private final McpClientService mcpClientService;
    private final ElasticsearchQueryGenerationService queryGenerationService;
    private final DateQueryProcessor dateQueryProcessor;
    private final ElasticsearchQueryService elasticsearchQueryService;
    
    // Store schema for index pattern extraction
    private Object esSchema;

    public ComplexSearchStrategy(McpClientService mcpClientService, 
                                ElasticsearchQueryGenerationService queryGenerationService,
                                DateQueryProcessor dateQueryProcessor,
                                ElasticsearchQueryService elasticsearchQueryService) {
        this.mcpClientService = mcpClientService;
        this.queryGenerationService = queryGenerationService;
        this.dateQueryProcessor = dateQueryProcessor;
        this.elasticsearchQueryService = elasticsearchQueryService;
    }

    @Override
    public UserIntent getSupportedIntent() {
        return UserIntent.COMPLEX_SEARCH;
    }

    @Override
    public Object execute(String userPrompt, IntentAnalysisResult analysisResult) {
        log.info("Executing COMPLEX_SEARCH strategy for prompt: {}", userPrompt);
        
        long startTime = System.currentTimeMillis();
        
        try {
            this.esSchema = mcpClientService.getElasticsearchSchema();
            
            String queryWithDdMmYyyy = queryGenerationService.generateQuery(
                userPrompt,
                esSchema
            );
            
            DateQueryProcessor.DateRange extractedDates = dateQueryProcessor.extractDatesFromQuery(queryWithDdMmYyyy);
            DateQueryProcessor.DateRange processedDates = dateQueryProcessor.applyDateRules(extractedDates.getGte(), extractedDates.getLte());
            
            long gteEpoch = dateQueryProcessor.convertToEpoch(processedDates.getGte(), true);
            long lteEpoch = dateQueryProcessor.convertToEpoch(processedDates.getLte(), false);
            String finalQuery = dateQueryProcessor.replaceDatesWithEpoch(queryWithDdMmYyyy, gteEpoch, lteEpoch);
            
            String startDateForHost = dateQueryProcessor.convertEpochToYyyyMmDd(gteEpoch);
            String endDateForHost = dateQueryProcessor.convertEpochToYyyyMmDd(lteEpoch);
            Object hostResult = mcpClientService.searchElasticsearchHost(startDateForHost, endDateForHost);
            String selectedHost = extractHostFromResult(hostResult);
            
            List<String> indices = determineIndices(startDateForHost, endDateForHost);
            Object searchResult = elasticsearchQueryService.executeSearch(
                finalQuery,
                selectedHost, 
                indices
            );
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Return simplified response format
            return buildSuccessResponse(
                userPrompt,
                selectedHost,
                finalQuery,
                searchResult,
                executionTime
            );
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Complex search failed after {}ms", executionTime, e);
            
            return buildErrorResponse(e, executionTime, userPrompt, analysisResult);
        }
    }


    /**
     * Extract host from MCP result
     */
    private String extractHostFromResult(Object hostResult) {
        if (hostResult != null) {
            String resultStr = hostResult.toString();
            if (resultStr.contains("PRIMARY")) return "PRIMARY";
            if (resultStr.contains("SECONDARY")) return "SECONDARY";
            if (resultStr.contains("ARCHIVE")) return "ARCHIVE";
        }
        return "PRIMARY";
    }

    /**
     * Determine indices based on date range and schema pattern
     * Generates monthly indices like: payment-history-03-2025*, payment-history-04-2025*
     */
    private List<String> determineIndices(String startDateForHost, String endDateForHost) {
        try {
            List<String> indices = new ArrayList<>();
            
            // Parse dates (format: yyyy-MM-dd)
            LocalDate startDate = LocalDate.parse(startDateForHost);
            LocalDate endDate = LocalDate.parse(endDateForHost);
            
            // Extract base pattern from schema
            String basePattern = extractIndexPatternFromSchema(this.esSchema);
            
            // Generate monthly indices for each month in the date range
            LocalDate currentMonth = startDate.withDayOfMonth(1); // Start of start month
            LocalDate lastMonthToInclude = endDate.withDayOfMonth(1); // First day of end month
            
            while (!currentMonth.isAfter(lastMonthToInclude)) {
                String monthlyIndex = String.format("%s%02d-%d*", 
                    basePattern, 
                    currentMonth.getMonthValue(), 
                    currentMonth.getYear()
                );
                indices.add(monthlyIndex);
                currentMonth = currentMonth.plusMonths(1);
            }
            
            log.info("Generated indices for date range {}-{}: {}", 
                    startDateForHost, endDateForHost, indices);
            
            return indices;
            
        } catch (Exception e) {
            log.error("Failed to determine indices, using default pattern", e);
            return List.of("payment-history-*"); // Fallback
        }
    }

    /**
     * Extract index pattern from Elasticsearch schema
     * From schema: "index_patterns" : [ "payment-history-*" ]
     * Returns: "payment-history-"
     */
    private String extractIndexPatternFromSchema(Object esSchema) {
        try {
            if (esSchema instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schemaMap = (Map<String, Object>) esSchema;
                
                // Navigate through the schema structure
                Object content = schemaMap.get("content");
                if (content instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> contentList = (List<Object>) content;
                    
                    for (Object item : contentList) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) item;
                            
                            Object data = itemMap.get("data");
                            if (data instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> dataMap = (Map<String, Object>) data;
                                
                                Object indexPatterns = dataMap.get("index_patterns");
                                if (indexPatterns instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> patterns = (List<String>) indexPatterns;
                                    
                                    if (!patterns.isEmpty()) {
                                        String pattern = patterns.get(0); // "payment-history-*"
                                        // Remove the wildcard and return base pattern
                                        return pattern.replace("*", "");   // "payment-history-"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            log.warn("Could not extract index pattern from schema, using default");
            return "payment-history-"; // Default fallback
            
        } catch (Exception e) {
            log.error("Error extracting index pattern from schema", e);
            return "payment-history-"; // Default fallback
        }
    }

    private Map<String, Object> buildSuccessResponse(String userPrompt, String esHost, String esQuery, Object esResponse, long executionTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("intent", "COMPLEX_SEARCH");
        response.put("userPrompt", userPrompt);
        response.put("executionTime", executionTime + "ms");
        response.put("esHost", esHost);
        response.put("esQuery", esQuery);
        
        if (esResponse instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = (Map<String, Object>) esResponse;
            response.put("esResponse", responseMap.get("content"));
        } else {
            response.put("esResponse", esResponse);
        }
        
        return response;
    }

    private Map<String, Object> buildErrorResponse(Exception e, long executionTime, String userPrompt, IntentAnalysisResult analysisResult) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("intent", "COMPLEX_SEARCH");
        response.put("userPrompt", userPrompt);
        response.put("executionTime", executionTime + "ms");
        response.put("error", Map.of(
            "message", e.getMessage(),
            "type", e.getClass().getSimpleName()
        ));
        
        if (analysisResult != null) {
            response.put("analysisInfo", Map.of(
                "confidence", analysisResult.getConfidence(),
                "reasoning", analysisResult.getAnalysisMetadata() != null ? analysisResult.getAnalysisMetadata() : "No reasoning provided"
            ));
        }
        
        return response;
    }

    @Override
    public String getDescription() {
        return "Executes 6-step search workflow: schema, LLM query generation, date processing, epoch conversion, host selection, search execution";
    }
}

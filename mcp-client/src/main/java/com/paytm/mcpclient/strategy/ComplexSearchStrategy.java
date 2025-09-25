package com.paytm.mcpclient.strategy;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.UserIntent;
import com.paytm.mcpclient.mcp.service.McpClientService;
import com.paytm.mcpclient.query.ElasticsearchQueryGenerationService;
import com.paytm.mcpclient.util.DateQueryProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    public ComplexSearchStrategy(McpClientService mcpClientService, 
                               ElasticsearchQueryGenerationService queryGenerationService,
                               DateQueryProcessor dateQueryProcessor) {
        this.mcpClientService = mcpClientService;
        this.queryGenerationService = queryGenerationService;
        this.dateQueryProcessor = dateQueryProcessor;
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
            Object esSchema = mcpClientService.getElasticsearchSchema();
            
            String queryWithDdMmYyyy = queryGenerationService.generateQuery(
                userPrompt,
                esSchema,
                "PRIMARY",
                null
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
            Object searchResult = mcpClientService.searchElasticsearch(
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

    private List<String> determineIndices(String startDate, String endDate) {
        return Arrays.asList("payment-history-*");
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

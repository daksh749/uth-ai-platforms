package com.paytm.mcpclient.strategy;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.UserIntent;
import com.paytm.mcpclient.mcp.service.McpClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for handling SCHEMA_ONLY intent.
 * This strategy simply retrieves the Elasticsearch schema/mapping information.
 * 
 * Examples of user prompts that trigger this strategy:
 * - "show me elasticsearch schema"
 * - "what fields are available"
 * - "give me field mapping"
 * - "elasticsearch structure"
 */
@Component
@Slf4j
public class SchemaOnlyStrategy implements IntentExecutionStrategy {

    private final McpClientService mcpClientService;

    public SchemaOnlyStrategy(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @Override
    public UserIntent getSupportedIntent() {
        return UserIntent.SCHEMA_ONLY;
    }

    @Override
    public Object execute(String userPrompt, IntentAnalysisResult analysisResult) {
        log.info("Executing SCHEMA_ONLY strategy for prompt: {}", userPrompt);
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object schemaResult = mcpClientService.getElasticsearchSchema();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Return structured response
            return buildSuccessResponse(userPrompt, schemaResult, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Schema retrieval failed after {}ms", executionTime, e);
            
            return buildErrorResponse(e, executionTime, userPrompt, analysisResult);
        }
    }

    private Map<String, Object> buildSuccessResponse(String userPrompt, Object schemaResult, long executionTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("intent", "SCHEMA_ONLY");
        response.put("userPrompt", userPrompt);
        response.put("executionTime", executionTime + "ms");
        response.put("result", Map.of(
            "type", "elasticsearch_schema",
            "data", schemaResult
        ));
        
        return response;
    }

    private Map<String, Object> buildErrorResponse(Exception e, long executionTime, String userPrompt, IntentAnalysisResult analysisResult) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("intent", "SCHEMA_ONLY");
        response.put("userPrompt", userPrompt);
        response.put("executionTime", executionTime + "ms");
        response.put("error", Map.of(
            "message", e.getMessage(),
            "type", e.getClass().getSimpleName()
        ));
        
        return response;
    }

    @Override
    public String getDescription() {
        return "Retrieves Elasticsearch schema/mapping information";
    }
}

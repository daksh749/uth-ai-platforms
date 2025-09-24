package com.paytm.mcpserver.controller;

import com.paytm.shared.elasticsearch.model.EsHostType;
import com.paytm.mcpserver.elasticsearch.service.EsSearchService;
import com.paytm.mcpserver.elasticsearch.service.EsSchemaService;
import com.paytm.mcpserver.elasticsearch.service.EsHostSearchService;
import com.paytm.mcpserver.transport.sse.SseConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {
    
    @Autowired
    private EsSearchService esSearchService;
    
    @Autowired
    private EsSchemaService esSchemaService;
    
    @Autowired
    private EsHostSearchService esHostSearchService;
    
    @Autowired
    private SseConnectionManager sseConnectionManager;
    
    /**
     * Test endpoint to verify ES Search tool functionality
     * GET /api/test/es-search?query=Blinkit&size=10
     */
    @GetMapping("/es-search")
    public Object testEsSearch(@RequestParam(name = "query", defaultValue = "Blinkit") String query,
                              @RequestParam(name = "size", defaultValue = "10") int size,
                              @RequestParam(name = "host", defaultValue = "PRIMARY") String host) {
        
        log.info("Testing ES Search with query: {}, size: {}, host: {}", query, size, host);
        
        try {
            // Create search query
            SearchSourceBuilder searchQuery = new SearchSourceBuilder()
                .query(QueryBuilders.matchQuery("searchFields.searchRemarks", query))
                .size(size)
                .sort("txnDate", SortOrder.DESC);
            
            // Execute search
            EsHostType hostType = EsHostType.valueOf(host.toUpperCase());
            Object result = esSearchService.executeSearch(
                searchQuery,
                hostType,
                Arrays.asList("payment-history-01-2025-2") // Updated to match your actual index
            );
            
            log.info("ES Search completed successfully");
            return result;
            
        } catch (Exception e) {
            log.error("ES Search failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("query", query);
            errorResponse.put("host", host);
            
            return errorResponse;
        }
    }
    
    /**
     * Test endpoint for complex queries
     * POST /api/test/es-search-complex
     */
    @PostMapping("/es-search-complex")
    public Object testComplexSearch(@RequestBody Map<String, Object> request) {
        
        log.info("Testing complex ES Search: {}", request);
        
        try {
            // Extract parameters
            int minAmount = (Integer) request.getOrDefault("minAmount", 1000);
            int maxAmount = (Integer) request.getOrDefault("maxAmount", 50000);
            String status = (String) request.getOrDefault("status", "2");
            int size = (Integer) request.getOrDefault("size", 20);
            
            // Create complex boolean query
            SearchSourceBuilder complexQuery = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                    .must(QueryBuilders.rangeQuery("amount").gte(minAmount).lte(maxAmount))
                    .filter(QueryBuilders.termQuery("status", status))
                    .filter(QueryBuilders.termQuery("txnIndicator", "2"))
                )
                .size(size)
                .sort("amount", SortOrder.DESC)
                .sort("txnDate", SortOrder.DESC);
            
            // Execute search
            Object result = esSearchService.executeSearch(
                complexQuery,
                EsHostType.PRIMARY,
                null // Search all indices
            );
            
            log.info("Complex ES Search completed successfully");
            return result;
            
        } catch (Exception e) {
            log.error("Complex ES Search failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("request", request);
            
            return errorResponse;
        }
    }
    
    /**
     * Test ES Schema tool
     * GET /api/test/es-schema
     */
    @GetMapping("/es-schema")
    public Object testEsSchema() {
        log.info("Testing ES Schema tool");
        
        try {
            Object result = esSchemaService.executeSchema();
            log.info("ES Schema tool executed successfully");
            return result;
            
        } catch (Exception e) {
            log.error("ES Schema tool failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("tool", "es_schema");
            
            return errorResponse;
        }
    }
    
    /**
     * Test ES Host Search tool - Default (no date)
     * GET /api/test/es-host-search
     */
    @GetMapping("/es-host-search")
    public Object testEsHostSearchDefault() {
        log.info("Testing ES Host Search tool (default - no date)");
        
        try {
            Object result = esHostSearchService.executeHostSearch(null, null);
            log.info("ES Host Search tool executed successfully (default)");
            return result;
            
        } catch (Exception e) {
            log.error("ES Host Search tool failed (default)", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("tool", "es_host_search");
            
            return errorResponse;
        }
    }
    
    /**
     * Test ES Host Search tool with date range
     * GET /api/test/es-host-search-with-date?startDate=2024-01-01&endDate=2024-01-31
     */
    @GetMapping("/es-host-search-with-date")
    public Object testEsHostSearchWithDate(
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate) {
        
        log.info("Testing ES Host Search tool with dates: {} to {}", startDate, endDate);
        
        try {
            Object result = esHostSearchService.executeHostSearch(startDate, endDate);
            log.info("ES Host Search tool executed successfully with dates");
            return result;
            
        } catch (Exception e) {
            log.error("ES Host Search tool failed with dates", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("tool", "es_host_search");
            errorResponse.put("input_start_date", startDate);
            errorResponse.put("input_end_date", endDate);
            
            return errorResponse;
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "MCP Tools");
        health.put("timestamp", System.currentTimeMillis());
        
        // Test tool metadata
        Map<String, Object> tools = new HashMap<>();
        tools.put("es_search", Map.of(
            "name", esSearchService.getName(),
            "required_params", esSearchService.getRequiredParameters(),
            "optional_params", esSearchService.getOptionalParameters()
        ));
        tools.put("es_schema", Map.of(
            "name", esSchemaService.getName(),
            "required_params", esSchemaService.getRequiredParameters(),
            "optional_params", esSchemaService.getOptionalParameters()
        ));
        tools.put("es_host_search", Map.of(
            "name", esHostSearchService.getName(),
            "required_params", esHostSearchService.getRequiredParameters(),
            "optional_params", esHostSearchService.getOptionalParameters()
        ));
        
        health.put("available_tools", tools);
        
        // SSE Transport information
        Map<String, Object> transport = new HashMap<>();
        transport.put("type", "SSE");
        transport.put("endpoint", "/mcp/sse");
        transport.put("active_connections", sseConnectionManager.getActiveConnectionCount());
        transport.put("total_connections", sseConnectionManager.getTotalConnectionCount());
        
        health.put("transport", transport);
        
        return health;
    }
}

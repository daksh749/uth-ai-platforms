package com.paytm.mcpserver.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.mcpserver.config.beans.ElasticsearchClientFactory;
import com.paytm.mcpserver.config.properties.ElasticsearchProperties;
import com.paytm.shared.elasticsearch.model.EsHostType;
import com.paytm.shared.mcp.tools.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class EsSearchService implements McpTool {
    
    @Autowired
    private ElasticsearchClientFactory clientFactory;
    
    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Override
    public String getName() {
        return "es_search";
    }
    
    /**
     * Execute Elasticsearch search with type-safe parameters
     * 
     * @param searchSourceBuilder ES search configuration with query, aggregations, etc.
     * @param esHost Target Elasticsearch host (PRIMARY, SECONDARY, TERTIARY)
     * @param indices List of indices to search (if empty, searches all payment-history-*)
     * @return Search results in structured format
     */
    public Object executeSearch(SearchSourceBuilder searchSourceBuilder, 
                               EsHostType esHost, 
                               List<String> indices) {
        
        // Validate parameters
        Objects.requireNonNull(searchSourceBuilder, "SearchSourceBuilder cannot be null");
        Objects.requireNonNull(esHost, "EsHostType cannot be null");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Use all payment-history indices if none specified
            List<String> targetIndices = (indices == null || indices.isEmpty()) 
                ? getAllPaymentHistoryIndices() 
                : indices;
            
            log.debug("Executing search on host: {}, indices: {}", esHost, targetIndices);
            
            // Execute search
            SearchResponse response = performSearch(searchSourceBuilder, targetIndices, esHost);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.debug("Search completed in {}ms, found {} results", 
                executionTime, response.getHits().getTotalHits().value);
            
            return buildSuccessResponse(response, targetIndices, esHost, executionTime);
            
        } catch (Exception e) {
            log.error("Search failed on host: {}, indices: {}", esHost, indices, e);
            return buildErrorResponse(e, esHost, indices);
        }
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        log.info("EsSearchService.execute called with parameters: {}", parameters.keySet());
        
        // Log each parameter value and type
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object value = entry.getValue();
            log.info("Parameter '{}': type={}, value={}", 
                entry.getKey(), 
                value != null ? value.getClass().getSimpleName() : "null", 
                value);
        }
        
        try {
            // Extract and convert to type-safe parameters
            SearchSourceBuilder searchSourceBuilder = (SearchSourceBuilder) parameters.get("searchSourceBuilder");
            EsHostType esHost = (EsHostType) parameters.get("esHost");
            @SuppressWarnings("unchecked")
            List<String> indices = (List<String>) parameters.get("indices");
            
            log.info("After extraction:");
            log.info("  searchSourceBuilder: {}", searchSourceBuilder != null ? "present" : "NULL");
            log.info("  esHost: {}", esHost);
            log.info("  indices: {}", indices);
            
            if (searchSourceBuilder != null) {
                log.info("  SearchSourceBuilder details - hasQuery: {}, query: {}, size: {}", 
                    searchSourceBuilder.query() != null, 
                    searchSourceBuilder.query(),
                    searchSourceBuilder.size());
            }
            
            return executeSearch(searchSourceBuilder, esHost, indices);
            
        } catch (ClassCastException e) {
            log.error("ClassCastException in parameter extraction", e);
            return buildErrorResponse(new IllegalArgumentException("Invalid parameter types", e), null, null);
        }
    }
    
    private SearchResponse performSearch(SearchSourceBuilder sourceBuilder, 
                                       List<String> indices, 
                                       EsHostType esHost) throws IOException {
        
        // Get the correct client for the selected host
        RestHighLevelClient client = clientFactory.getClient(esHost);
        
        // Create search request
        SearchRequest searchRequest = new SearchRequest(indices.toArray(new String[0]));
        searchRequest.source(sourceBuilder);
        
        log.debug("Executing search on host: {} with client: {}", esHost.getName(), client.getClass().getSimpleName());
        
        // Execute search using the dynamically selected client
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }
    
    private List<String> getAllPaymentHistoryIndices() throws IOException {
        // For now, return a default list - in production you'd use cat indices API
        // This is a simplified implementation
        return Arrays.asList("payment-history-2024-01", "payment-history-2024-02");
    }
    
    private Object buildSuccessResponse(SearchResponse response, 
                                       List<String> indices, 
                                       EsHostType esHost, 
                                       long executionTime) {
        try {
            // Build structured response with host information
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("timestamp", Instant.now().toString());
            result.put("execution_time_ms", executionTime);
            
            // Host information
            result.put("selected_host", esHost.name());
            result.put("host_url", clientFactory.getHostConfig(esHost).getUrl());
            result.put("searched_indices", indices);
            
            // Search results
            Map<String, Object> searchResults = new HashMap<>();
            searchResults.put("total_hits", response.getHits().getTotalHits().value);
            searchResults.put("max_score", response.getHits().getMaxScore());
            searchResults.put("took_ms", response.getTook().millis());
            
            // Convert hits to structured format
            List<Map<String, Object>> hits = new ArrayList<>();
            response.getHits().forEach(hit -> {
                Map<String, Object> hitMap = new HashMap<>();
                hitMap.put("index", hit.getIndex());
                hitMap.put("id", hit.getId());
                hitMap.put("score", hit.getScore());
                hitMap.put("source", hit.getSourceAsMap());
                hits.add(hitMap);
            });
            searchResults.put("hits", hits);
            
            result.put("search_results", searchResults);
            
            log.info("Search completed successfully on host: {} with {} results", esHost.getName(), response.getHits().getTotalHits().value);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to build success response", e);
            throw new RuntimeException("Failed to build search response", e);
        }
    }
    
    private Map<String, Object> buildErrorResponse(Exception e, EsHostType esHost, List<String> indices) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("timestamp", Instant.now().toString());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("code", "ES_SEARCH_FAILED");
        errorDetails.put("message", "Elasticsearch search failed");
        errorDetails.put("details", e.getMessage());
        
        if (esHost != null) {
            errorDetails.put("host_type", esHost.name());
        }
        if (indices != null) {
            errorDetails.put("indices", indices);
        }
        
        error.put("error", errorDetails);
        return error;
    }

    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList("searchSourceBuilder", "esHost");
    }
    
    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList("indices");
    }
}
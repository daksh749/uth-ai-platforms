package com.paytm.mcpclient.elasticsearch.service;

import com.paytm.mcpclient.llm.service.LlmService;
import com.paytm.mcpclient.mcp.service.McpClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch search service with enhanced rules-based LLM integration
 * Focuses on the main ChatTestController flow using intelligent query generation
 */
@Slf4j
@Service
public class EsSearchService {
    
    @Autowired
    private McpClientService mcpClientService;
    
    @Autowired
    private LlmService llmService;
    
    /**
     * Execute simple search flow: Schema → LLM → Query → Host → Search → Results
     * This is the main method used by ChatTestController with enhanced rules-based intelligence.
     */
    public Object executeSimpleSearchFlow(String userPrompt) {
        log.info("Starting enhanced search flow with rules for user prompt: {}", userPrompt);
        
        try {
            // Step 1: Get schema
            Object schema = mcpClientService.getElasticsearchSchema();
            log.debug("Retrieved ES schema, sending to LLM for intelligent processing with rules");
            
            // Step 2: LLM orchestrates tools with enhanced rules
            Object searchResults = llmService.generateQueryWithFlow(userPrompt, schema.toString());
            log.debug("Enhanced search completed, returning raw results");
            
            // Return raw search results without formatting
            return searchResults;
            
        } catch (Exception throwable) {
            log.error("Enhanced search flow failed for prompt: {}", userPrompt, throwable);
            return "Search failed: " + throwable.getMessage();
        }
    }
}
package com.paytm.mcpclient.elasticsearch.service;

import com.paytm.mcpclient.llm.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EsSearchService {
    
    @Autowired
    private LlmService llmService;
    
    public Object executeSimpleSearchFlow(String userPrompt) {
        log.info("Starting search flow for user prompt: {}", userPrompt);
        
        try {
            return llmService.processUserPromptWithIntentAnalysis(userPrompt);
        } catch (Exception e) {
            log.error("Search flow failed for prompt: {}", userPrompt, e);
            return "Search failed: " + e.getMessage();
        }
    }
}
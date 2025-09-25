package com.paytm.mcpclient.llm.service;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.IntentAnalyzerService;
import com.paytm.mcpclient.orchestrator.IntentExecutionOrchestrator;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class LlmService {
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private IntentAnalyzerService intentAnalyzerService;
    
    @Autowired
    private IntentExecutionOrchestrator intentExecutionOrchestrator;
    
    public Object processUserPromptWithIntentAnalysis(String userPrompt) {
        log.info("Processing user prompt with intent-driven architecture: {}", userPrompt);
        
        long startTime = System.currentTimeMillis();
        
        try {
            IntentAnalysisResult analysisResult = intentAnalyzerService.analyzeIntent(userPrompt);
            
            if (!analysisResult.isSuccess()) {
                log.error("Intent analysis failed: {}", analysisResult.getError());
                return Map.of(
                    "status", "error",
                    "message", "Failed to analyze user intent: " + analysisResult.getError(),
                    "executionTime", (System.currentTimeMillis() - startTime) + "ms"
                );
            }
            
            log.info("Intent analyzed: {} (confidence: {:.2f})", 
                analysisResult.getIntent(), analysisResult.getConfidence());
            
            Object result = intentExecutionOrchestrator.executeIntent(userPrompt, analysisResult);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Intent-driven processing completed in {}ms", totalTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Intent-driven processing failed after {}ms", executionTime, e);
            
            return Map.of(
                "status", "error",
                "message", "Intent-driven processing failed: " + e.getMessage(),
                "executionTime", executionTime + "ms",
                "error", e.getClass().getSimpleName()
            );
        }
    }

    public Boolean testConnection() {
        try {
            String testResponse = chatLanguageModel.generate("Test connection. Respond with 'OK'");
            return testResponse != null && !testResponse.trim().isEmpty();
        } catch (Exception e) {
            log.error("LLM connection test failed", e);
            return false;
        }
    }
}
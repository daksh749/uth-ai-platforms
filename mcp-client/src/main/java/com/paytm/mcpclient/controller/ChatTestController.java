package com.paytm.mcpclient.controller;

import com.paytm.mcpclient.llm.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatTestController {
    
    @Autowired
    private LlmService llmService;
    
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> processUserQuery(
            @RequestBody Map<String, Object> request) {
        
        String userPrompt = (String) request.get("prompt");
        
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            Map<String, Object> errorResponse = Map.of(
                "error", "User prompt is required",
                "example", Map.of("prompt", "Show me transactions between 1st Sept to 5th Sept with status 2"),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        log.info("Processing user query: {}", userPrompt);
        
        try {
            Object result = llmService.processUserPromptWithIntentAnalysis(userPrompt);
            
            Map<String, Object> response = Map.of(
                "userPrompt", userPrompt,
                "result", result,
                "status", "success",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Processing failed for prompt: {}", userPrompt, e);
            
            Map<String, Object> errorResponse = Map.of(
                "error", "Processing failed: " + e.getMessage(),
                "userPrompt", userPrompt,
                "status", "error",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
package com.paytm.mcpclient.controller;

import com.paytm.mcpclient.elasticsearch.service.EsSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatTestController {
    
    @Autowired
    private EsSearchService esSearchService;
    
    /**
     * Main chat endpoint for Postman testing
     * Accepts user prompt and uses LLM intelligence to determine host/indices
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> processUserQuery(
            @RequestBody Map<String, Object> request) {
        
        String userPrompt = (String) request.get("prompt");
        
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            Map<String, Object> errorResponse = Map.of(
                "error", "User prompt is required",
                "example", Map.of(
                    "prompt", "Show me failed payments from last week"
                ),
                "note", "The LLM will intelligently determine the host and indices based on your query",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        log.info("Processing user query with ENHANCED RULES-BASED search flow: '{}'", userPrompt);
        
        try {
            Object formattedResponse = esSearchService.executeSimpleSearchFlow(userPrompt);
            
            Map<String, Object> response = Map.of(
                "userPrompt", userPrompt,
                "response", formattedResponse,
                "status", "success",
                "note", "Query processed using enhanced rules-based LLM with comprehensive field mapping, date handling, and error prevention",
                "flow_type", "enhanced_rules_based",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception throwable) {
            log.error("Failed to process user query: '{}'", userPrompt, throwable);
            Map<String, Object> errorResponse = Map.of(
                "userPrompt", userPrompt,
                "error", throwable.getMessage(),
                "status", "failed",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "MCP Chat Client",
            "endpoints", Map.of(
                "chat", "POST /api/chat/query",
                "health", "GET /api/chat/health"
            ),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Get example queries for testing
     */
    @GetMapping("/examples")
    public ResponseEntity<Map<String, Object>> getExampleQueries() {
        List<Map<String, Object>> examples = List.of(
            Map.of(
                "prompt", "transactions between 1st Sept 2025 to 5th Sept 2025 with status 2",
                "description", "Date range query with exact status matching - showcases rules-based date handling and status field precision"
            ),
            Map.of(
                "prompt", "find transaction with RRN ABC123",
                "description", "Multi-field RRN search - demonstrates field mapping rules across searchFields and nested participants"
            ),
            Map.of(
                "prompt", "Show me payments above 1000 rupees on 10th Sept 2025",
                "description", "Amount range with single date - shows amount conversion and single date to range logic"
            ),
            Map.of(
                "prompt", "Find UPI payments for mobile number 9876543210",
                "description", "Mobile number search - demonstrates nested query rules for participants.mobileData"
            ),
            Map.of(
                "prompt", "transactions with card number 1234567890123456",
                "description", "Card number search - shows nested query for participants.cardData.cardNum"
            ),
            Map.of(
                "prompt", "show me failed transactions today",
                "description", "Status and date query - demonstrates date handling for 'today' and status interpretation"
            )
        );
        
        return ResponseEntity.ok(Map.of(
            "examples", examples,
            "usage", Map.of(
                "endpoint", "POST /api/chat/query",
                "body", Map.of(
                    "prompt", "Your natural language query here"
                ),
                "note", "Enhanced rules-based LLM with comprehensive field mapping, date handling, nested queries, and error prevention",
                "features", List.of(
                    "Intelligent field mapping (RRN, mobile, card numbers)",
                    "Smart date handling (single date = range to now)",
                    "Exact status value matching (no interpretation)",
                    "Nested queries for participant data",
                    "Automatic host and index selection"
                )
            ),
            "timestamp", System.currentTimeMillis()
        ));
    }
}

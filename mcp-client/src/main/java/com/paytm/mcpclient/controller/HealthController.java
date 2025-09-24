package com.paytm.mcpclient.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "MCP Client",
            "timestamp", Instant.now().toString(),
            "message", "MCP Client is running successfully"
        ));
    }
    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "application", "MCP Client",
            "version", "1.0.0-SNAPSHOT",
            "description", "MCP Chat Client with LLM Integration",
            "dependencies", Map.of(
                "spring-boot", "3.4.10",
                "langchain4j", "0.25.0",
                "elasticsearch", "7.17.15",
                "okhttp-eventsource", "4.1.1"
            )
        ));
    }
}

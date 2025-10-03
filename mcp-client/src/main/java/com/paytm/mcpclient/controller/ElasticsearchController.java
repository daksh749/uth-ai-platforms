package com.paytm.mcpclient.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class ElasticsearchController {

    private final ChatClient chatClient;

    public ElasticsearchController(ChatClient.Builder chatClientBuilder){
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/chat")
    public String processQuery(@RequestBody QueryRequest request) {
        log.info("Processing Elasticsearch prompt: {}", request.prompt());
        
        try {
            // Process query using ChatClient with MCP tools
            return chatClient.prompt(request.prompt).call().content();
            
        } catch (Exception e) {
            log.error("Failed to process Elasticsearch prompt", e);
            return "failure";
        }
    }

    
    // Request DTO
    public record QueryRequest(
        String prompt
    ) {}
}

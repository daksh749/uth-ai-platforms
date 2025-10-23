package com.paytm.mcpclient.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class ElasticsearchController {

    private final ChatClient chatClient;

    public ElasticsearchController(ChatClient chatClient){
        this.chatClient = chatClient;
    }

    @PostMapping("/chat")
    public String processQuery(@RequestBody QueryRequest request) {
        log.info("Processing Elasticsearch prompt: {}", request.prompt());
        
        try {
            // Process query using ChatClient with MCP tools
            String response = chatClient.prompt()
                    .user(request.prompt())
                    .call()
                    .content();

            return response;
            
        } catch (Exception e) {
            log.error("Failed to process Elasticsearch prompt", e);
            return "Error: " + e.getMessage();
        }
    }

    
    // Request DTO
    public record QueryRequest(
        String prompt
    ) {}
}

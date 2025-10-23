package com.paytm.mcpclient.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ChatClient with Ollama integration and MCP tool support
 */
@Configuration
public class ChatClientConfig {

    /**
     * Configure ChatClient with Ollama chat model and MCP tools
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ToolCallbackProvider tools) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                    You are an expert Elasticsearch assistant with access to payment transaction data.
                    
                    Available MCP Tools:
                    - es_schema: Get Elasticsearch field mappings and schema information
                    - es_host: Determine optimal Elasticsearch hosts based on date ranges (use DD-MM-YYYY format)
                    - es_indices: Get relevant index names for specific date ranges (use DD-MM-YYYY format)
                    - es_query: Convert natural language queries to Elasticsearch DSL with schema context
                    - es_search: Execute Elasticsearch queries and return formatted results
                    
                    Guidelines:
                    1. Analyze the user query and determine the best tool to use.
                    2. If user asks for any query or to fetch results from ES, ALWAYS call es_schema first to understand field mappings
                    3. Use es_host and es_indices for date-based queries to optimize performance
                    4. Convert user queries to proper Elasticsearch DSL using es_query with schema context
                    5. Execute searches with es_search and format results clearly
                    6. Use DD-MM-YYYY format for all dates (e.g., 28-09-2025)
                    7. Handle errors gracefully and provide helpful suggestions
                    
                    IMPORTANT: Always execute the tools and provide the actual results, not just the tool call information.
                    
                    Provide clear, concise responses with relevant data insights.
                    """)
                .defaultToolCallbacks(tools)
                .build();
    }
}

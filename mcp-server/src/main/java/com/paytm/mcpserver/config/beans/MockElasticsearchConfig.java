package com.paytm.mcpserver.config.beans;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Mock Elasticsearch configuration for testing without real ES cluster
 * Activate with: spring.elasticsearch.mock=true
 * 
 * Note: Mockito dependency would be needed for full mock implementation
 */
@Configuration
@ConditionalOnProperty(name = "spring.elasticsearch.mock", havingValue = "true")
public class MockElasticsearchConfig {
    
    @Bean
    @Primary
    public RestHighLevelClient mockElasticsearchClient() {
        // Return a null client for testing - would need Mockito for proper mocking
        // return mock(RestHighLevelClient.class);
        return null;
    }
}

package com.paytm.mcpserver.config.beans;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration class.
 * 
 * Note: This class previously created a single hardcoded PRIMARY client,
 * but has been replaced by ElasticsearchClientFactory which creates
 * dynamic clients based on host selection (PRIMARY/SECONDARY/TERTIARY).
 * 
 * Keeping this class for potential future global ES configuration needs.
 */
@Configuration
@Slf4j
public class ElasticsearchConfig {
    
    // No longer creating a single hardcoded client
    // Dynamic client creation is now handled by ElasticsearchClientFactory
    
    static {
        log.info("ElasticsearchConfig initialized - using dynamic client factory for host-based routing");
    }
}

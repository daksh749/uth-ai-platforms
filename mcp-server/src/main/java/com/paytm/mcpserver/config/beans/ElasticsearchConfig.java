package com.paytm.mcpserver.config.beans;

import com.paytm.mcpserver.config.properties.ElasticsearchProperties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;

@Configuration
public class ElasticsearchConfig {
    
    @Autowired
    private ElasticsearchProperties elasticsearchProperties;
    
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        // Get primary host configuration
        ElasticsearchProperties.HostConfig primaryHost = elasticsearchProperties.getHosts().stream()
            .filter(host -> "primary".equals(host.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Primary Elasticsearch host not configured"));
        
        try {
            // Parse URL
            URL url = new URL(primaryHost.getUrl());
            
            // Create high-level client directly
            return new RestHighLevelClient(
                RestClient.builder(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Elasticsearch client", e);
        }
    }
}

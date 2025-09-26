package com.paytm.mcpserver.config.beans;

import com.paytm.mcpserver.config.properties.ElasticsearchProperties;
import com.paytm.shared.elasticsearch.model.EsHostType;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory service for creating and managing Elasticsearch REST clients
 * dynamically based on host type selection.
 */
@Service
@Slf4j
public class ElasticsearchClientFactory {
    
    @Autowired
    private ElasticsearchProperties elasticsearchProperties;
    
    // Cache clients to avoid recreating them for each request
    private final Map<String, RestHighLevelClient> clientCache = new ConcurrentHashMap<>();
    
    /**
     * Get or create a REST client for the specified host type
     * 
     * @param hostType The target Elasticsearch host type (PRIMARY, SECONDARY, TERTIARY)
     * @return RestHighLevelClient configured for the specified host
     * @throws IllegalStateException if host configuration is not found
     * @throws RuntimeException if client creation fails
     */
    public RestHighLevelClient getClient(EsHostType hostType) {
        String hostName = hostType.getName();
        
        return clientCache.computeIfAbsent(hostName, key -> {
            log.info("Creating new Elasticsearch client for host: {}", hostName);
            
            // Find host configuration
            ElasticsearchProperties.HostConfig hostConfig = elasticsearchProperties.getHosts().stream()
                .filter(host -> hostName.equals(host.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Host configuration not found for: " + hostName));
            
            try {
                // Parse host URL
                URL url = new URL(hostConfig.getUrl());
                
                log.debug("Creating client for host: {} -> {}://{}:{}", 
                    hostName, url.getProtocol(), url.getHost(), url.getPort());
                
                // Create REST client with proper configuration
                RestClientBuilder builder = RestClient.builder(
                    new HttpHost(url.getHost(), url.getPort(), url.getProtocol())
                );
                
                // Configure timeouts from host config
                builder.setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder
                        .setConnectTimeout(hostConfig.getTimeout())
                        .setSocketTimeout(hostConfig.getTimeout())
                );
                
                // Configure HTTP client with connection pool settings
                builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder
                        .setMaxConnTotal(elasticsearchProperties.getConnection().getPoolSize())
                        .setMaxConnPerRoute(elasticsearchProperties.getConnection().getPoolSize() / 2)
                        .setKeepAliveStrategy((response, context) -> 
                            elasticsearchProperties.getConnection().isKeepAlive() ? 300000 : -1) // 5 minutes
                );
                
                RestHighLevelClient client = new RestHighLevelClient(builder);
                
                log.info("Successfully created Elasticsearch client for host: {} at {}", hostName, hostConfig.getUrl());
                
                return client;
                
            } catch (Exception e) {
                log.error("Failed to create Elasticsearch client for host: {}", hostName, e);
                throw new RuntimeException("Failed to create Elasticsearch client for " + hostName, e);
            }
        });
    }
    
    /**
     * Get host configuration for the specified host type
     * 
     * @param hostType The target host type
     * @return Host configuration details
     */
    public ElasticsearchProperties.HostConfig getHostConfig(EsHostType hostType) {
        return elasticsearchProperties.getHosts().stream()
            .filter(host -> hostType.getName().equals(host.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Host configuration not found for: " + hostType.getName()));
    }
    
    /**
     * Check if a client exists for the specified host type
     * 
     * @param hostType The host type to check
     * @return true if client exists in cache, false otherwise
     */
    public boolean hasClient(EsHostType hostType) {
        return clientCache.containsKey(hostType.getName());
    }
    
    /**
     * Get the number of cached clients
     * 
     * @return Number of active clients in cache
     */
    public int getCachedClientCount() {
        return clientCache.size();
    }
    
    /**
     * Close and remove a specific client from cache
     * 
     * @param hostType The host type whose client should be closed
     */
    public void closeClient(EsHostType hostType) {
        String hostName = hostType.getName();
        RestHighLevelClient client = clientCache.remove(hostName);
        
        if (client != null) {
            try {
                log.info("Closing Elasticsearch client for host: {}", hostName);
                client.close();
            } catch (IOException e) {
                log.error("Error closing Elasticsearch client for host: {}", hostName, e);
            }
        }
    }
    
    /**
     * Close all cached clients when the application shuts down
     */
    @PreDestroy
    public void closeAllClients() {
        log.info("Closing all Elasticsearch clients ({} clients)", clientCache.size());
        
        for (Map.Entry<String, RestHighLevelClient> entry : clientCache.entrySet()) {
            try {
                log.debug("Closing client for host: {}", entry.getKey());
                entry.getValue().close();
            } catch (IOException e) {
                log.error("Error closing Elasticsearch client for host: {}", entry.getKey(), e);
            }
        }
        
        clientCache.clear();
        log.info("All Elasticsearch clients closed successfully");
    }
}

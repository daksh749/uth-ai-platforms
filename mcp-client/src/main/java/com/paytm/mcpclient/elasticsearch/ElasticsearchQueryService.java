package com.paytm.mcpclient.elasticsearch;

import com.paytm.mcpclient.mcp.service.McpClientService;
import com.paytm.mcpclient.redash.RedashClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service that routes Elasticsearch queries to appropriate execution service
 * based on the active profile. Uses Redash in production, direct MCP in other environments.
 */
@Service
@Slf4j
public class ElasticsearchQueryService {
    
    @Autowired
    private Environment environment;
    
    @Autowired(required = false)
    private RedashClientService redashClientService;
    
    @Autowired
    private McpClientService mcpClientService;
    
    /**
     * Execute Elasticsearch search with profile-based routing
     * 
     * @param query The Elasticsearch query (can be SearchSourceBuilder or JSON string)
     * @param hostType The ES host type (primary, secondary, tertiary)
     * @param indices List of indices to query
     * @return Query results from the appropriate service
     */
    public Object executeSearch(Object query, String hostType, List<String> indices) {
        String[] activeProfiles = environment.getActiveProfiles();
        log.info("Executing ES search for host type: {} with active profiles: {}", hostType, Arrays.toString(activeProfiles));
        
        if (isProductionProfile() && redashClientService != null) {
            return executeViaRedash(query, hostType, indices);
        } else {
            return executeViaMcp(query, hostType, indices);
        }
    }
    
    /**
     * Execute search via Redash (production profile)
     */
    private Object executeViaRedash(Object query, String hostType, List<String> indices) {
        log.info("Routing ES query to Redash for production environment");
        
        try {
            String jsonQuery = convertQueryToJson(query);
            return redashClientService.executeQuery(jsonQuery, hostType, indices);
            
        } catch (Exception e) {
            log.error("Redash query execution failed, attempting fallback to MCP", e);
            
            // Fallback to MCP if Redash fails
            return executeViaMcp(query, hostType, indices);
        }
    }
    
    /**
     * Execute search via MCP (non-production profiles)
     */
    private Object executeViaMcp(Object query, String hostType, List<String> indices) {
        log.info("Routing ES query to MCP service");
        
        try {
            return mcpClientService.searchElasticsearch(query, hostType, indices);
            
        } catch (Exception e) {
            log.error("MCP query execution failed", e);
            throw new RuntimeException("Failed to execute ES query via MCP: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if current profile is production
     */
    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("prod") || 
               Arrays.asList(activeProfiles).contains("production");
    }
    
    /**
     * Convert query object to JSON string format expected by Redash
     */
    private String convertQueryToJson(Object query) {
        if (query instanceof String) {
            return (String) query;
        }
        
        // If it's a SearchSourceBuilder or other object, convert to JSON
        // This might need adjustment based on your specific query object types
        try {
            if (query.toString().startsWith("{")) {
                return query.toString();
            } else {
                // For SearchSourceBuilder objects, we might need special handling
                log.warn("Query object type not recognized, using toString(): {}", query.getClass().getSimpleName());
                return query.toString();
            }
        } catch (Exception e) {
            log.error("Failed to convert query to JSON", e);
            throw new RuntimeException("Failed to convert query to JSON format", e);
        }
    }
    
    /**
     * Check if Redash service is available and healthy
     */
    public boolean isRedashAvailable() {
        return redashClientService != null && isProductionProfile();
    }
    
    /**
     * Check if MCP service is available and healthy
     */
    public boolean isMcpAvailable() {
        try {
            return mcpClientService != null && mcpClientService.isConnected();
        } catch (Exception e) {
            log.error("Failed to check MCP availability", e);
            return false;
        }
    }
    
    /**
     * Get current execution strategy info
     */
    public String getExecutionStrategy() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (isProductionProfile() && redashClientService != null) {
            return "Redash (Production) - Active profiles: " + Arrays.toString(activeProfiles);
        } else {
            return "MCP (Direct) - Active profiles: " + Arrays.toString(activeProfiles);
        }
    }
}

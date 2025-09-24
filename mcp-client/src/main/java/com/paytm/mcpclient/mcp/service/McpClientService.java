package com.paytm.mcpclient.mcp.service;

import com.paytm.mcpclient.mcp.connection.McpConnectionManager;
import com.paytm.mcpclient.mcp.model.McpMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class McpClientService {
    
    @Autowired
    private McpConnectionManager connectionManager;
    
    /**
     * List all available tools from the MCP server
     */
    public List<Map<String, Object>> listTools() {
        log.info("Requesting list of available MCP tools");
        
        McpMessage request = McpMessage.request(
            UUID.randomUUID().toString(),
            "tools/list",
            Map.of()
        );
        
        try {
            McpMessage response = connectionManager.sendMessage(request);
            
            if (response.getError() != null) {
                throw new RuntimeException("MCP Error: " + response.getError().getMessage());
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            
            log.info("Received {} tools from MCP server", tools != null ? tools.size() : 0);
            return tools;
            
        } catch (Exception e) {
            log.error("Failed to list MCP tools", e);
            throw new RuntimeException("Failed to list MCP tools: " + e.getMessage(), e);
        }
    }
    
    /**
     * Call a specific MCP tool with parameters
     */
    public Object callTool(String toolName, Map<String, Object> arguments) {
        log.info("Calling MCP tool: {} with arguments: {}", toolName, arguments);
        
        Map<String, Object> params = Map.of(
            "name", toolName,
            "arguments", arguments != null ? arguments : Map.of()
        );
        
        McpMessage request = McpMessage.request(
            UUID.randomUUID().toString(),
            "tools/call",
            params
        );
        
        try {
            McpMessage response = connectionManager.sendMessage(request);
            
            if (response.getError() != null) {
                throw new RuntimeException("MCP Tool Error: " + response.getError().getMessage());
            }
            
            log.info("Successfully called MCP tool: {}", toolName);
            return response.getResult();
            
        } catch (Exception e) {
            log.error("Failed to call MCP tool: {}", toolName, e);
            throw new RuntimeException("Failed to call MCP tool '" + toolName + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Ping the MCP server to check connectivity
     */
    public Boolean ping() {
        log.debug("Pinging MCP server");
        
        McpMessage request = McpMessage.request(
            UUID.randomUUID().toString(),
            "ping",
            Map.of()
        );
        
        try {
            McpMessage response = connectionManager.sendMessage(request);
            boolean success = response.getError() == null;
            log.debug("MCP server ping result: {}", success);
            return success;
            
        } catch (Exception e) {
            log.error("Failed to ping MCP server", e);
            return false;
        }
    }
    
    /**
     * Call ES search tool specifically
     */
    public Object searchElasticsearch(Object searchSourceBuilder, String esHost, List<String> indices) {
        Map<String, Object> arguments = Map.of(
            "searchSourceBuilder", searchSourceBuilder,
            "esHost", esHost,
            "indices", indices != null ? indices : List.of()
        );
        
        return callTool("es_search", arguments);
    }
    
    /**
     * Call ES schema tool to get field mappings
     */
    public Object getElasticsearchSchema() {
        return callTool("es_schema", Map.of());
    }
    
    /**
     * Call ES host search tool
     */
    public Object searchElasticsearchByHost(Object searchSourceBuilder, String esHost) {
        Map<String, Object> arguments = Map.of(
            "searchSourceBuilder", searchSourceBuilder,
            "esHost", esHost
        );
        
        return callTool("es_host_search", arguments);
    }
    
    /**
     * Check if MCP connection is healthy
     */
    public boolean isConnected() {
        return connectionManager.isConnected();
    }
    
    /**
     * Get current connection ID
     */
    public String getConnectionId() {
        return connectionManager.getConnectionId();
    }
}

package com.paytm.shared.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * Represents a JSON-RPC 2.0 request for MCP (Model Context Protocol)
 * 
 * Standard JSON-RPC 2.0 format:
 * {
 *   "jsonrpc": "2.0",
 *   "id": "request-id",
 *   "method": "tools/list",
 *   "params": {...}
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpRequest {
    
    /**
     * JSON-RPC version (always "2.0")
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    /**
     * Request identifier (can be string or number)
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * Method name (e.g., "tools/list", "tools/call")
     */
    @JsonProperty("method")
    private String method;
    
    /**
     * Method parameters (optional)
     */
    @JsonProperty("params")
    private Map<String, Object> params;
    
    /**
     * Check if this is a valid JSON-RPC 2.0 request
     */
    public boolean isValid() {
        return "2.0".equals(jsonrpc) && 
               method != null && 
               !method.trim().isEmpty();
    }
    
    /**
     * Check if this is a notification (no id field)
     */
    public boolean isNotification() {
        return id == null;
    }
    
    /**
     * Get parameter value by key
     */
    public Object getParam(String key) {
        return params != null ? params.get(key) : null;
    }
    
    /**
     * Get parameter value by key with default
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, T defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        try {
            return (T) params.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    /**
     * Check if parameter exists
     */
    public boolean hasParam(String key) {
        return params != null && params.containsKey(key);
    }
}

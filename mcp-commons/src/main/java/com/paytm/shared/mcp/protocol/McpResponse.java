package com.paytm.shared.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a JSON-RPC 2.0 response for MCP (Model Context Protocol)
 * 
 * Success response format:
 * {
 *   "jsonrpc": "2.0",
 *   "id": "request-id",
 *   "result": {...}
 * }
 * 
 * Error response format:
 * {
 *   "jsonrpc": "2.0", 
 *   "id": "request-id",
 *   "error": {
 *     "code": -32600,
 *     "message": "Invalid Request",
 *     "data": {...}
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {
    
    /**
     * JSON-RPC version (always "2.0")
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    /**
     * Request identifier (matches the request id)
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * Success result (present only on success)
     */
    @JsonProperty("result")
    private Object result;
    
    /**
     * Error details (present only on error)
     */
    @JsonProperty("error")
    private McpError error;
    
    /**
     * Create a success response
     */
    public static McpResponse success(String id, Object result) {
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setResult(result);
        return response;
    }
    
    /**
     * Create an error response
     */
    public static McpResponse error(String id, McpError error) {
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setError(error);
        return response;
    }
    
    /**
     * Create an error response with code and message
     */
    public static McpResponse error(String id, int code, String message) {
        return error(id, new McpError(code, message, null));
    }
    
    /**
     * Create an error response with code, message, and data
     */
    public static McpResponse error(String id, int code, String message, Object data) {
        return error(id, new McpError(code, message, data));
    }
    
    /**
     * Check if this is a success response
     */
    public boolean isSuccess() {
        return error == null && result != null;
    }
    
    /**
     * Check if this is an error response
     */
    public boolean isError() {
        return error != null;
    }
}

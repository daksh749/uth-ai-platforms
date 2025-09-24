package com.paytm.shared.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a JSON-RPC 2.0 error object for MCP
 * 
 * Error format:
 * {
 *   "code": -32600,
 *   "message": "Invalid Request",
 *   "data": {...}  // optional
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpError {
    
    /**
     * Error code (JSON-RPC 2.0 standard codes)
     */
    @JsonProperty("code")
    private int code;
    
    /**
     * Error message
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * Additional error data (optional)
     */
    @JsonProperty("data")
    private Object data;
    
    // JSON-RPC 2.0 Standard Error Codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    
    // MCP-specific error codes (using reserved range -32000 to -32099)
    public static final int TOOL_NOT_FOUND = -32000;
    public static final int TOOL_EXECUTION_ERROR = -32001;
    public static final int INVALID_TOOL_PARAMS = -32002;
    public static final int CONNECTION_ERROR = -32003;
    
    /**
     * Create a parse error
     */
    public static McpError parseError(String details) {
        return new McpError(PARSE_ERROR, "Parse error", details);
    }
    
    /**
     * Create an invalid request error
     */
    public static McpError invalidRequest(String details) {
        return new McpError(INVALID_REQUEST, "Invalid Request", details);
    }
    
    /**
     * Create a method not found error
     */
    public static McpError methodNotFound(String method) {
        return new McpError(METHOD_NOT_FOUND, "Method not found", 
            String.format("Method '%s' is not supported", method));
    }
    
    /**
     * Create an invalid params error
     */
    public static McpError invalidParams(String details) {
        return new McpError(INVALID_PARAMS, "Invalid params", details);
    }
    
    /**
     * Create an internal error
     */
    public static McpError internalError(String details) {
        return new McpError(INTERNAL_ERROR, "Internal error", details);
    }
    
    /**
     * Create a tool not found error
     */
    public static McpError toolNotFound(String toolName) {
        return new McpError(TOOL_NOT_FOUND, "Tool not found", 
            String.format("Tool '%s' is not available", toolName));
    }
    
    /**
     * Create a tool execution error
     */
    public static McpError toolExecutionError(String toolName, String details) {
        return new McpError(TOOL_EXECUTION_ERROR, "Tool execution failed", 
            String.format("Tool '%s' failed: %s", toolName, details));
    }
    
    /**
     * Create an invalid tool params error
     */
    public static McpError invalidToolParams(String toolName, String details) {
        return new McpError(INVALID_TOOL_PARAMS, "Invalid tool parameters", 
            String.format("Tool '%s' received invalid parameters: %s", toolName, details));
    }
    
    /**
     * Create a connection error
     */
    public static McpError connectionError(String details) {
        return new McpError(CONNECTION_ERROR, "Connection error", details);
    }
}

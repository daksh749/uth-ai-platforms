package com.paytm.mcpserver.mcp.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.shared.mcp.protocol.McpError;
import com.paytm.shared.mcp.protocol.McpRequest;
import com.paytm.shared.mcp.protocol.McpResponse;
import com.paytm.mcpserver.mcp.registry.ParameterMapper;
import com.paytm.shared.mcp.registry.ToolMetadata;
import com.paytm.mcpserver.mcp.registry.ToolRegistry;
import com.paytm.shared.mcp.tools.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main MCP Protocol Handler for processing JSON-RPC 2.0 messages
 * Handles core MCP methods like tools/list and tools/call
 */
@Service
@Slf4j
public class McpProtocolHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private ToolRegistry toolRegistry;
    
    @Autowired
    private ParameterMapper parameterMapper;
    
    // MCP method constants
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_PING = "ping";
    
    /**
     * Main entry point for processing MCP messages
     * 
     * @param jsonMessage Raw JSON-RPC message
     * @param connectionId SSE connection ID for context
     * @return McpResponse to send back to client
     */
    public McpResponse handleMessage(String jsonMessage, String connectionId) {
        log.debug("Processing MCP message from connection {}: {}", connectionId, jsonMessage);
        
        try {
            // Parse JSON-RPC request
            McpRequest request = parseRequest(jsonMessage);
            
            // Validate request
            if (!request.isValid()) {
                log.warn("Invalid JSON-RPC request from connection {}: {}", connectionId, jsonMessage);
                return McpResponse.error(request.getId(), McpError.invalidRequest("Invalid JSON-RPC 2.0 format"));
            }
            
            // Route to appropriate handler
            McpResponse response = routeMessage(request, connectionId);
            
            log.debug("MCP message processed successfully for connection {}, method: {}", 
                connectionId, request.getMethod());
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to process MCP message from connection {}: {}", connectionId, jsonMessage, e);
            return McpResponse.error(null, McpError.internalError("Failed to process message: " + e.getMessage()));
        }
    }
    
    /**
     * Parse JSON string into McpRequest
     */
    private McpRequest parseRequest(String jsonMessage) throws Exception {
        try {
            return objectMapper.readValue(jsonMessage, McpRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse JSON-RPC request: {}", jsonMessage, e);
            throw new Exception("Parse error: " + e.getMessage());
        }
    }
    
    /**
     * Route message to appropriate handler based on method
     */
    private McpResponse routeMessage(McpRequest request, String connectionId) {
        String method = request.getMethod();
        
        log.debug("Routing MCP method '{}' for connection {}", method, connectionId);
        
        switch (method) {
            case METHOD_TOOLS_LIST:
                return handleToolsList(request, connectionId);
                
            case METHOD_TOOLS_CALL:
                return handleToolsCall(request, connectionId);
                
            case METHOD_PING:
                return handlePing(request, connectionId);
                
            default:
                log.warn("Unknown MCP method '{}' from connection {}", method, connectionId);
                return McpResponse.error(request.getId(), McpError.methodNotFound(method));
        }
    }
    
    /**
     * Handle tools/list request - return available tools
     */
    private McpResponse handleToolsList(McpRequest request, String connectionId) {
        log.debug("Handling tools/list request from connection {}", connectionId);
        
        try {
            // Phase 2: Use ToolRegistry for dynamic discovery
            List<ToolMetadata> toolsMetadata = toolRegistry.getAllToolsMetadata();
            
            // Convert to MCP format
            List<Map<String, Object>> tools = toolsMetadata.stream()
                .map(ToolMetadata::getSchema)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("tools", tools);
            result.put("_meta", Map.of(
                "server", "UTH AI Systems MCP Server",
                "version", "2.0.0",
                "timestamp", Instant.now().toString(),
                "tool_count", tools.size(),
                "registry_enabled", true
            ));
            
            log.debug("Returning {} dynamically discovered tools for connection {}", 
                tools.size(), connectionId);
            
            return McpResponse.success(request.getId(), result);
            
        } catch (Exception e) {
            log.error("Failed to handle tools/list for connection {}", connectionId, e);
            return McpResponse.error(request.getId(), 
                McpError.internalError("Failed to retrieve tools list: " + e.getMessage()));
        }
    }
    
    /**
     * Handle tools/call request - execute a specific tool
     */
    private McpResponse handleToolsCall(McpRequest request, String connectionId) {
        log.debug("Handling tools/call request from connection {}", connectionId);
        
        try {
            // Extract tool name and arguments
            String toolName = request.getParam("name", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = request.getParam("arguments", new HashMap<>());
            
            if (toolName.isEmpty()) {
                return McpResponse.error(request.getId(), 
                    McpError.invalidParams("Tool name is required"));
            }
            
            log.debug("Tool call request - tool: '{}', args: {} from connection {}", 
                toolName, arguments.keySet(), connectionId);
            
            // Phase 2: Real tool execution
            
            // 1. Check if tool exists
            if (!toolRegistry.hasTool(toolName)) {
                log.warn("Tool not found: {} from connection {}", toolName, connectionId);
                return McpResponse.error(request.getId(), McpError.toolNotFound(toolName));
            }
            
            // 2. Get tool and metadata
            McpTool tool = toolRegistry.getTool(toolName);
            ToolMetadata toolMetadata = toolRegistry.getToolMetadata(toolName);
            
            // 3. Map parameters from JSON-RPC format to tool format
            Map<String, Object> mappedParams = parameterMapper.mapParametersForTool(toolName, arguments);
            
            // 4. Validate parameters
            if (!parameterMapper.validateParameters(toolName, mappedParams, toolMetadata)) {
                return McpResponse.error(request.getId(), 
                    McpError.invalidToolParams(toolName, "Parameter validation failed"));
            }
            
            // 5. Execute the tool
            log.debug("Executing tool '{}' with mapped parameters: {}", toolName, mappedParams.keySet());
            Object toolResult = tool.execute(mappedParams);
            
            // 6. Format result for MCP response
            Map<String, Object> result = formatToolResult(toolName, toolResult, arguments);
            
            log.debug("Tool '{}' executed successfully for connection {}", toolName, connectionId);
            
            return McpResponse.success(request.getId(), result);
            
        } catch (Exception e) {
            log.error("Failed to handle tools/call for connection {}", connectionId, e);
            return McpResponse.error(request.getId(), 
                McpError.toolExecutionError(request.getParam("name", "unknown"), e.getMessage()));
        }
    }
    
    /**
     * Handle ping request - simple health check
     */
    private McpResponse handlePing(McpRequest request, String connectionId) {
        log.debug("Handling ping request from connection {}", connectionId);
        
        Map<String, Object> result = Map.of(
            "pong", true,
            "timestamp", Instant.now().toString(),
            "server", "UTH AI Systems MCP Server",
            "connection_id", connectionId
        );
        
        return McpResponse.success(request.getId(), result);
    }
    
    /**
     * Format tool result for MCP response
     */
    private Map<String, Object> formatToolResult(String toolName, Object toolResult, Map<String, Object> originalArgs) {
        Map<String, Object> result = new HashMap<>();
        
        // Create MCP content format
        java.util.List<Map<String, Object>> content = new java.util.ArrayList<>();
        
        // Add text summary
        content.add(Map.of(
            "type", "text",
            "text", String.format("Tool '%s' executed successfully", toolName)
        ));
        
        // Add the actual tool result as JSON
        content.add(Map.of(
            "type", "json",
            "data", toolResult
        ));
        
        // Add execution metadata
        content.add(Map.of(
            "type", "json",
            "data", Map.of(
                "_meta", Map.of(
                    "tool", toolName,
                    "timestamp", Instant.now().toString(),
                    "original_arguments", originalArgs,
                    "execution_mode", "real"
                )
            )
        ));
        
        result.put("content", content);
        return result;
    }
}

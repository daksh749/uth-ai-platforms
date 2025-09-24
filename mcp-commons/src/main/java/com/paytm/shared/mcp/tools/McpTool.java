package com.paytm.shared.mcp.tools;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interface for MCP (Model Context Protocol) tools
 */
public interface McpTool {
    
    /**
     * Get the name of this MCP tool
     * 
     * @return Tool name (e.g., "es_search", "es_schema")
     */
    String getName();
    
    /**
     * Execute the tool with given parameters (for MCP compatibility)
     * 
     * @param parameters Tool parameters as map
     * @return Tool execution result
     */
    Object execute(Map<String, Object> parameters);
    
    /**
     * Get list of required parameters for this tool
     * 
     * @return List of required parameter names
     */
    default List<String> getRequiredParameters() {
        return Collections.emptyList();
    }
    
    /**
     * Get list of optional parameters for this tool
     * 
     * @return List of optional parameter names
     */
    default List<String> getOptionalParameters() {
        return Collections.emptyList();
    }
}

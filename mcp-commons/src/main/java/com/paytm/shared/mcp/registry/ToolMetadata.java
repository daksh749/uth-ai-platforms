package com.paytm.shared.mcp.registry;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Metadata for an MCP tool including schema information
 */
@Data
@Builder
public class ToolMetadata {
    
    /**
     * Tool name (unique identifier)
     */
    private String name;
    
    /**
     * Tool description
     */
    private String description;
    
    /**
     * Required parameters
     */
    private List<String> requiredParameters;
    
    /**
     * Optional parameters
     */
    private List<String> optionalParameters;
    
    /**
     * Complete JSON schema for the tool
     */
    private Map<String, Object> schema;
    
    /**
     * Get total parameter count
     */
    public int getTotalParameterCount() {
        int count = 0;
        if (requiredParameters != null) {
            count += requiredParameters.size();
        }
        if (optionalParameters != null) {
            count += optionalParameters.size();
        }
        return count;
    }
    
    /**
     * Check if tool has required parameters
     */
    public boolean hasRequiredParameters() {
        return requiredParameters != null && !requiredParameters.isEmpty();
    }
    
    /**
     * Check if tool has optional parameters
     */
    public boolean hasOptionalParameters() {
        return optionalParameters != null && !optionalParameters.isEmpty();
    }
    
    /**
     * Check if parameter is required
     */
    public boolean isParameterRequired(String parameterName) {
        return requiredParameters != null && requiredParameters.contains(parameterName);
    }
    
    /**
     * Check if parameter is optional
     */
    public boolean isParameterOptional(String parameterName) {
        return optionalParameters != null && optionalParameters.contains(parameterName);
    }
    
    /**
     * Check if parameter is supported
     */
    public boolean supportsParameter(String parameterName) {
        return isParameterRequired(parameterName) || isParameterOptional(parameterName);
    }
}

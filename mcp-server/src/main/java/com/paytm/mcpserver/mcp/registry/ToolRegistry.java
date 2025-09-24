package com.paytm.mcpserver.mcp.registry;

import com.paytm.shared.mcp.tools.McpTool;
import com.paytm.shared.mcp.registry.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for auto-discovering and managing MCP tools
 * Automatically finds all McpTool implementations and provides metadata
 */
@Service
@Slf4j
public class ToolRegistry {
    
    @Autowired
    private List<McpTool> allTools;
    
    private Map<String, McpTool> toolsByName;
    private List<ToolMetadata> toolMetadata;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Tool Registry...");
        
        // Create name-based lookup map
        toolsByName = allTools.stream()
            .collect(Collectors.toMap(
                McpTool::getName,
                tool -> tool,
                (existing, replacement) -> {
                    log.warn("Duplicate tool name found: {}. Using first occurrence.", existing.getName());
                    return existing;
                }
            ));
        
        // Generate metadata for all tools
        toolMetadata = allTools.stream()
            .map(this::createToolMetadata)
            .collect(Collectors.toList());
        
        log.info("Tool Registry initialized with {} tools: {}", 
            toolsByName.size(), 
            toolsByName.keySet());
    }
    
    /**
     * Get all available tools metadata
     */
    public List<ToolMetadata> getAllToolsMetadata() {
        return new ArrayList<>(toolMetadata);
    }
    
    /**
     * Get tool by name
     */
    public McpTool getTool(String toolName) {
        return toolsByName.get(toolName);
    }
    
    /**
     * Check if tool exists
     */
    public boolean hasTool(String toolName) {
        return toolsByName.containsKey(toolName);
    }
    
    /**
     * Get all tool names
     */
    public Set<String> getToolNames() {
        return new HashSet<>(toolsByName.keySet());
    }
    
    /**
     * Get tool count
     */
    public int getToolCount() {
        return toolsByName.size();
    }
    
    /**
     * Get tool metadata by name
     */
    public ToolMetadata getToolMetadata(String toolName) {
        return toolMetadata.stream()
            .filter(meta -> meta.getName().equals(toolName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Generate JSON schema for a specific tool
     */
    public Map<String, Object> generateToolSchema(String toolName) {
        McpTool tool = getTool(toolName);
        if (tool == null) {
            return null;
        }
        
        return generateToolSchema(tool);
    }
    
    /**
     * Generate JSON schema for a tool
     */
    public Map<String, Object> generateToolSchema(McpTool tool) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", tool.getName());
        schema.put("description", getToolDescription(tool));
        
        // Generate input schema
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        // Generate properties from tool parameters
        Map<String, Object> properties = generateParameterProperties(tool);
        inputSchema.put("properties", properties);
        
        // Set required parameters
        List<String> required = tool.getRequiredParameters();
        if (required != null && !required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        schema.put("inputSchema", inputSchema);
        
        return schema;
    }
    
    /**
     * Create tool metadata from McpTool instance
     */
    private ToolMetadata createToolMetadata(McpTool tool) {
        return ToolMetadata.builder()
            .name(tool.getName())
            .description(getToolDescription(tool))
            .requiredParameters(tool.getRequiredParameters())
            .optionalParameters(tool.getOptionalParameters())
            .schema(generateToolSchema(tool))
            .build();
    }
    
    /**
     * Get tool description (with fallback)
     */
    private String getToolDescription(McpTool tool) {
        // Try to get description from tool if it has a getDescription method
        try {
            if (tool.getClass().getMethod("getDescription") != null) {
                return (String) tool.getClass().getMethod("getDescription").invoke(tool);
            }
        } catch (Exception e) {
            // Fallback to generating description from tool name
        }
        
        // Generate description based on tool name
        return generateDescriptionFromName(tool.getName());
    }
    
    /**
     * Generate parameter properties for JSON schema
     */
    private Map<String, Object> generateParameterProperties(McpTool tool) {
        Map<String, Object> properties = new HashMap<>();
        
        // Add required parameters
        List<String> required = tool.getRequiredParameters();
        if (required != null) {
            for (String param : required) {
                properties.put(param, inferParameterSchema(tool.getName(), param, true));
            }
        }
        
        // Add optional parameters
        List<String> optional = tool.getOptionalParameters();
        if (optional != null) {
            for (String param : optional) {
                properties.put(param, inferParameterSchema(tool.getName(), param, false));
            }
        }
        
        return properties;
    }
    
    /**
     * Infer parameter schema based on tool name and parameter name
     */
    private Map<String, Object> inferParameterSchema(String toolName, String paramName, boolean required) {
        Map<String, Object> paramSchema = new HashMap<>();
        
        // Tool-specific parameter schemas
        switch (toolName) {
            case "es_search":
                return inferEsSearchParameterSchema(paramName, required);
            case "es_schema":
                return inferEsSchemaParameterSchema(paramName, required);
            case "es_host_search":
                return inferEsHostSearchParameterSchema(paramName, required);
            default:
                // Generic parameter schema
                paramSchema.put("type", "string");
                paramSchema.put("description", "Parameter: " + paramName);
                return paramSchema;
        }
    }
    
    /**
     * Generate parameter schema for es_search tool
     */
    private Map<String, Object> inferEsSearchParameterSchema(String paramName, boolean required) {
        switch (paramName) {
            case "searchSourceBuilder":
                return Map.of(
                    "type", "object",
                    "description", "Elasticsearch SearchSourceBuilder configuration",
                    "properties", Map.of(
                        "query", Map.of("type", "object", "description", "Elasticsearch query"),
                        "size", Map.of("type", "integer", "description", "Number of results to return"),
                        "from", Map.of("type", "integer", "description", "Starting offset"),
                        "sort", Map.of("type", "array", "description", "Sort configuration")
                    )
                );
            case "esHost":
                return Map.of(
                    "type", "string",
                    "enum", Arrays.asList("PRIMARY", "SECONDARY", "TERTIARY"),
                    "description", "Target Elasticsearch host"
                );
            case "indices":
                return Map.of(
                    "type", "array",
                    "items", Map.of("type", "string"),
                    "description", "List of indices to search"
                );
            default:
                return Map.of("type", "string", "description", "Parameter: " + paramName);
        }
    }
    
    /**
     * Generate parameter schema for es_schema tool
     */
    private Map<String, Object> inferEsSchemaParameterSchema(String paramName, boolean required) {
        // es_schema has no parameters
        return Map.of("type", "string", "description", "Parameter: " + paramName);
    }
    
    /**
     * Generate parameter schema for es_host_search tool
     */
    private Map<String, Object> inferEsHostSearchParameterSchema(String paramName, boolean required) {
        switch (paramName) {
            case "startDate":
                return Map.of(
                    "type", "string",
                    "format", "date-time",
                    "description", "Start date for host selection (optional)"
                );
            case "endDate":
                return Map.of(
                    "type", "string", 
                    "format", "date-time",
                    "description", "End date for host selection (optional)"
                );
            default:
                return Map.of("type", "string", "description", "Parameter: " + paramName);
        }
    }
    
    /**
     * Generate description from tool name
     */
    private String generateDescriptionFromName(String toolName) {
        switch (toolName) {
            case "es_search":
                return "Execute Elasticsearch queries with SearchSourceBuilder";
            case "es_schema":
                return "Load Elasticsearch schema from JSON file and extract searchable fields";
            case "es_host_search":
                return "Select appropriate Elasticsearch host based on date range";
            default:
                return "MCP Tool: " + toolName;
        }
    }
}

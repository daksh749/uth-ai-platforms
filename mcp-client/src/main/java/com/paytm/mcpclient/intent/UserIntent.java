package com.paytm.mcpclient.intent;

/**
 * Enum representing different types of user intents for MCP tool execution
 * This drives the strategy selection for different execution paths
 */
public enum UserIntent {
    
    /**
     * User wants only the Elasticsearch schema/mapping information
     * Examples: "show me schema", "what fields are available", "elasticsearch mapping"
     * Tools required: es_schema only
     */
    SCHEMA_ONLY("Retrieve Elasticsearch schema information only"),
    
    /**
     * User wants only host selection/recommendation  
     * Examples: "best host for September", "which server for this data", "optimal host"
     * Tools required: es_host_search only
     */
    HOST_ONLY("Find optimal Elasticsearch host for given criteria"),
    
    /**
     * User wants to search for actual data/transactions with filters
     * Examples: "find transactions", "payments between dates", "status = 2"
     * Tools required: es_schema → es_host_search → es_search sequence
     */
    COMPLEX_SEARCH("Execute complex search with schema, host selection, and query execution");
    
    private final String description;
    
    UserIntent(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the required MCP tools for this intent
     */
    public String[] getRequiredTools() {
        return switch (this) {
            case SCHEMA_ONLY -> new String[]{"es_schema"};
            case HOST_ONLY -> new String[]{"es_host_search"};
            case COMPLEX_SEARCH -> new String[]{"es_schema", "es_host_search", "es_search"};
        };
    }
    
    /**
     * Check if this intent requires multiple tool execution
     */
    public boolean isMultiTool() {
        return this == COMPLEX_SEARCH;
    }
    
    /**
     * Check if this intent is actionable
     */
    public boolean isActionable() {
        return true;
    }
    
    /**
     * Get execution complexity level
     */
    public int getComplexityLevel() {
        return switch (this) {
            case SCHEMA_ONLY, HOST_ONLY -> 1;
            case COMPLEX_SEARCH -> 3;
        };
    }
}

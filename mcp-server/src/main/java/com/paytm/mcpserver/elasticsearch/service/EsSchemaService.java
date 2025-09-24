package com.paytm.mcpserver.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.shared.mcp.tools.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class EsSchemaService implements McpTool {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SCHEMA_FILE_PATH = "schemas/elasticsearch-schema.json";
    
    @Override
    public String getName() {
        return "es_schema";
    }
    
    /**
     * Execute ES schema retrieval from JSON file
     * 
     * @return Exact raw Elasticsearch schema JSON without any formatting or wrapper
     */
    public Object executeSchema() {
        try {
            long startTime = System.currentTimeMillis();
            
            log.debug("Loading raw ES schema from JSON file: {}", SCHEMA_FILE_PATH);
            
            // Load and return the exact raw schema from JSON file
            Map<String, Object> schemaData = loadSchemaFromFile();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.debug("Raw schema loaded successfully in {}ms", executionTime);
            
            // Return the exact raw schema without any wrapper or formatting
            return schemaData;
            
        } catch (Exception e) {
            log.error("Failed to load ES schema from JSON file", e);
            return buildErrorResponse(e);
        }
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        // ES Schema tool doesn't need parameters - ignore them
        return executeSchema();
    }
    
    /**
     * Load ES schema from JSON file in classpath
     */
    private Map<String, Object> loadSchemaFromFile() throws IOException {
        ClassPathResource resource = new ClassPathResource(SCHEMA_FILE_PATH);
        
        if (!resource.exists()) {
            throw new RuntimeException("Schema file not found: " + SCHEMA_FILE_PATH);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode schemaNode = objectMapper.readTree(inputStream);
            
            // Convert JsonNode to Map for easier manipulation
            @SuppressWarnings("unchecked")
            Map<String, Object> schemaMap = objectMapper.convertValue(schemaNode, Map.class);
            
            log.debug("Schema loaded from file successfully");
            return schemaMap;
        }
    }
    
    /**
     * Build error response only for exceptional cases
     */
    private Map<String, Object> buildErrorResponse(Exception e) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("timestamp", Instant.now().toString());
        error.put("source", "json_file");
        error.put("file_path", SCHEMA_FILE_PATH);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("code", "SCHEMA_LOAD_FAILED");
        errorDetails.put("message", "Failed to load schema from JSON file");
        errorDetails.put("details", e.getMessage());
        
        error.put("error", errorDetails);
        return error;
    }
    
    @Override
    public List<String> getRequiredParameters() {
        return Collections.emptyList(); // No parameters required
    }
    
    @Override
    public List<String> getOptionalParameters() {
        return Collections.emptyList(); // No parameters at all
    }
}
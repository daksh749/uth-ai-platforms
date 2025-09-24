package com.paytm.mcpclient.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for loading and managing Elasticsearch query generation rules
 * Provides dynamic access to rules without hardcoding in application logic
 */
@Service
@Slf4j
public class EsQueryRulesService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String RULES_FILE_PATH = "rules/elasticsearch-query-rules.json";
    
    private Map<String, Object> cachedRules;
    
    /**
     * Get all rules as JSON string for LLM prompt injection
     * This method is the primary interface for the LLM service
     * 
     * @return Complete rules as formatted JSON string
     */
    public String getQueryRulesAsJson() {
        try {
            Map<String, Object> rules = loadRules();
            // Pretty print for better LLM readability
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
        } catch (Exception e) {
            log.error("Failed to serialize rules to JSON", e);
            return "{}";
        }
    }
    
    /**
     * Get rules as Map for programmatic access
     * Useful for specific rule lookups or processing
     * 
     * @return Rules as Map structure
     */
    public Map<String, Object> getRulesMap() {
        return loadRules();
    }
    
    /**
     * Get specific rule category
     * 
     * @param category Rule category name (e.g., "field_mapping_rules", "date_handling_rules")
     * @return Rules for the specified category, empty map if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRuleCategory(String category) {
        Map<String, Object> rules = loadRules();
        Object categoryRules = rules.get(category);
        
        if (categoryRules instanceof Map) {
            return (Map<String, Object>) categoryRules;
        }
        
        log.warn("Rule category '{}' not found or not a Map", category);
        return new HashMap<>();
    }
    
    /**
     * Check if rules are loaded and available
     * 
     * @return true if rules are successfully loaded, false otherwise
     */
    public boolean areRulesAvailable() {
        Map<String, Object> rules = loadRules();
        return !rules.isEmpty();
    }
    
    /**
     * Get rules metadata information
     * 
     * @return Metadata about the rules (version, description, etc.)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRulesMetadata() {
        Map<String, Object> rules = loadRules();
        Object metadata = rules.get("metadata");
        
        if (metadata instanceof Map) {
            return (Map<String, Object>) metadata;
        }
        
        return new HashMap<>();
    }
    
    /**
     * Load rules from JSON file with caching
     * 
     * @return Rules as Map structure
     */
    private Map<String, Object> loadRules() {
        // Return cached rules if available
        if (cachedRules != null) {
            return cachedRules;
        }
        
        try {
            ClassPathResource resource = new ClassPathResource(RULES_FILE_PATH);
            
            if (!resource.exists()) {
                log.warn("Rules file not found: {}. Using empty rules.", RULES_FILE_PATH);
                cachedRules = new HashMap<>();
                return cachedRules;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rulesNode = objectMapper.readTree(inputStream);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> rulesMap = objectMapper.convertValue(rulesNode, Map.class);
                
                cachedRules = rulesMap;
                
                Map<String, Object> metadata = getRulesMetadata();
                String version = (String) metadata.get("version");
                String description = (String) metadata.get("description");
                
                log.info("Successfully loaded query rules - Version: {}, Description: {}", version, description);
                log.debug("Loaded {} rule categories from: {}", rulesMap.keySet().size(), RULES_FILE_PATH);
                
                return rulesMap;
            }
            
        } catch (IOException e) {
            log.error("Failed to load rules from: {}", RULES_FILE_PATH, e);
            cachedRules = new HashMap<>();
            return cachedRules;
        }
    }
    
    /**
     * Refresh rules cache - useful for development/testing
     * Forces reload of rules from file on next access
     */
    public void refreshRules() {
        log.info("Refreshing rules cache");
        cachedRules = null;
        
        // Trigger reload
        Map<String, Object> rules = loadRules();
        log.info("Rules refreshed successfully, loaded {} categories", rules.keySet().size());
    }
    
    /**
     * Get rules file path for debugging/logging
     * 
     * @return Path to the rules file
     */
    public String getRulesFilePath() {
        return RULES_FILE_PATH;
    }
}

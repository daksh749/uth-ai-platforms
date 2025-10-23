package com.paytm.mcpserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * ES Schema Fetcher Service
 *
 * Simple service that reads Elasticsearch schema from a JSON file
 * and returns it as-is without any processing.
 */
@Service
@Slf4j
public class ElasticsearchSchemaFetcher {

    @Value("${elasticsearch.schema.file:classpath:schemas/elasticsearch-schema.json}")
    private String schemaFilePath;

    @Value("${elasticsearch.fieldmappings.file:classpath:schemas/field-value-mappings.json}")
    private String fieldMappingsFilePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetch ES schema from JSON file
     *
     * @return Schema object exactly as stored in the JSON file
     */
    public String fetchSchema() {
        try {
            log.info("Loading ES schema from JSON file: {}", schemaFilePath);

            // Load schema from classpath JSON file
            ClassPathResource resource = new ClassPathResource(schemaFilePath.replace("classpath:", ""));

            if (!resource.exists()) {
                log.error("Schema file not found: {}", schemaFilePath);
                throw new RuntimeException("Schema file not found: " + schemaFilePath);
            }

            // Read and return the schema as-is
            try (InputStream inputStream = resource.getInputStream()) {
                String schema = objectMapper.readValue(inputStream, Object.class).toString();
                log.info("ES schema loaded successfully from file");
                return schema;
            }

        } catch (Exception e) {
            log.error("Failed to load ES schema from file: {}", schemaFilePath, e);
            return buildErrorResponse(e);
        }
    }

    /**
     * Fetch field value mappings from JSON file
     *
     * @return Field value mappings exactly as stored in the JSON file
     */
    public String fetchFieldMappings() {
        try {
            log.info("Loading field value mappings from JSON file: {}", fieldMappingsFilePath);

            // Load mappings from classpath JSON file
            ClassPathResource resource = new ClassPathResource(fieldMappingsFilePath.replace("classpath:", ""));

            if (!resource.exists()) {
                log.error("Field mappings file not found: {}", fieldMappingsFilePath);
                throw new RuntimeException("Field mappings file not found: " + fieldMappingsFilePath);
            }

            // Read and return the mappings as-is
            try (InputStream inputStream = resource.getInputStream()) {
                String mappings = objectMapper.readValue(inputStream, Object.class).toString();
                log.info("Field value mappings loaded successfully from file");
                return mappings;
            }

        } catch (Exception e) {
            log.error("Failed to load field value mappings from file: {}", fieldMappingsFilePath, e);
            return buildErrorResponse(e);
        }
    }

    /**
     * Build error response when schema loading fails
     */
    private String buildErrorResponse(Exception e) {
        return ("Failed to load es Schema" + e);
    }
}

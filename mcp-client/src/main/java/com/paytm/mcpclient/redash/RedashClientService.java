package com.paytm.mcpclient.redash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redash client service for executing Elasticsearch queries via Redash API
 * Only active in production profile to bypass direct ES connectivity issues
 */
@Service
@Profile("prod")
@Slf4j
public class RedashClientService {
    
    @Value("${redash.base-url}")
    private String redashBaseUrl;
    
    @Value("${redash.api-key}")
    private String apiKey;
    
    @Value("${redash.timeout:30000}")
    private long timeout;
    
    @Value("${redash.poll-interval:2000}")
    private long pollInterval;
    
    @Value("${redash.max-poll-attempts:15}")
    private int maxPollAttempts;
    
    // Data source mapping
    @Value("${redash.datasources.primary-id}")
    private Integer primaryDataSourceId;
    
    @Value("${redash.datasources.secondary-id}")
    private Integer secondaryDataSourceId;
    
    @Value("${redash.datasources.tertiary-id}")
    private Integer tertiaryDataSourceId;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public RedashClientService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Execute Elasticsearch query via Redash
     * 
     * @param esQuery The Elasticsearch query as JSON string
     * @param hostType The ES host type (primary, secondary, tertiary)
     * @param indices List of indices to query
     * @return Query results from Elasticsearch via Redash
     */
    public Object executeQuery(String esQuery, String hostType, List<String> indices) {
        log.info("Executing ES query via Redash for host type: {}", hostType);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Build the complete query with index
            String completeQuery = buildQueryWithIndex(esQuery, indices);
            
            // 2. Create query in Redash
            Integer queryId = createRedashQuery(completeQuery, hostType);
            
            // 3. Get results directly (Redash executes automatically)
            Object results = getQueryResults(queryId);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Redash query execution completed in {}ms", executionTime);
            
            return results;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Redash query execution failed after {}ms for host: {}", executionTime, hostType, e);
            throw new RuntimeException("Failed to execute query via Redash: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build complete query with index information
     */
    private String buildQueryWithIndex(String esQuery, List<String> indices) {
        try {
            JsonNode queryNode = objectMapper.readTree(esQuery);
            
            // Determine index pattern
            String indexPattern = determineIndexPattern(indices);
            
            // Build complete query object
            Map<String, Object> completeQuery = new HashMap<>();
            completeQuery.put("index", indexPattern);
            completeQuery.put("query", queryNode.get("query"));
            
            // Add size if present
            if (queryNode.has("size")) {
                completeQuery.put("size", queryNode.get("size").asInt());
            }
            
            return objectMapper.writeValueAsString(completeQuery);
            
        } catch (Exception e) {
            log.error("Failed to build query with index", e);
            throw new RuntimeException("Failed to build query with index", e);
        }
    }
    
    /**
     * Determine index pattern from indices list
     */
    private String determineIndexPattern(List<String> indices) {
        if (indices == null || indices.isEmpty()) {
            return "payment-history-*"; // Default pattern
        }
        
        if (indices.size() == 1) {
            return indices.get(0);
        }
        
        // For multiple indices, use comma-separated or find common pattern
        return String.join(",", indices);
    }
    
    /**
     * Create query in Redash
     */
    private Integer createRedashQuery(String query, String hostType) {
        log.debug("Creating Redash query for host type: {}", hostType);
        
        try {
            String url = redashBaseUrl + "/api/queries";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("data_source_id", getDataSourceId(hostType));
            requestBody.put("name", "Auto-generated ES Query - " + System.currentTimeMillis());
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Failed to create Redash query: " + response.getStatusCode());
            }
            
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            Integer queryId = responseNode.get("id").asInt();
            
            log.debug("Created Redash query with ID: {}", queryId);
            return queryId;
            
        } catch (Exception e) {
            log.error("Failed to create Redash query", e);
            throw new RuntimeException("Failed to create Redash query", e);
        }
    }
    
    /**
     * Execute query in Redash
     */
    private String executeRedashQuery(Integer queryId) {
        log.debug("Executing Redash query ID: {}", queryId);
        
        try {
            // Correct URL for executing/refreshing a query
            String url = redashBaseUrl + "/api/queries/" + queryId + "/refresh";
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Failed to execute Redash query: " + response.getStatusCode());
            }
            
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            
            // Check if response has job information
            if (!responseNode.has("job")) {
                log.error("No job information in response: {}", response.getBody());
                throw new RuntimeException("Invalid response from Redash: missing job information");
            }
            
            JsonNode jobNode = responseNode.get("job");
            if (jobNode == null || !jobNode.has("id")) {
                log.error("Invalid job node in response: {}", response.getBody());
                throw new RuntimeException("Invalid job information in Redash response");
            }
            
            String jobId = jobNode.get("id").asText();
            
            log.debug("Started Redash job with ID: {}", jobId);
            return jobId;
            
        } catch (Exception e) {
            log.error("Failed to execute Redash query", e);
            throw new RuntimeException("Failed to execute Redash query", e);
        }
    }
    
    /**
     * Poll for query results with exponential backoff
     */
    private Object pollForResults(String jobId, Integer queryId) {
        log.debug("Polling for results of job: {}", jobId);
        
        int attempt = 0;
        long currentInterval = pollInterval;
        
        while (attempt < maxPollAttempts) {
            try {
                // Check job status
                JobStatus jobStatus = checkJobStatus(jobId);
                
                if (jobStatus.isCompleted()) {
                    if (jobStatus.isSuccess()) {
                        return getQueryResults(queryId);
                    } else {
                        throw new RuntimeException("Redash query failed: " + jobStatus.getError());
                    }
                }
                
                // Wait before next attempt
                Thread.sleep(currentInterval);
                
                // Exponential backoff (but cap at 10 seconds)
                currentInterval = Math.min(currentInterval * 2, 10000);
                attempt++;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            } catch (Exception e) {
                log.error("Error during polling attempt {}", attempt, e);
                attempt++;
                
                if (attempt >= maxPollAttempts) {
                    throw new RuntimeException("Failed to get results after " + maxPollAttempts + " attempts", e);
                }
            }
        }
        
        throw new RuntimeException("Query execution timed out after " + maxPollAttempts + " attempts");
    }
    
    /**
     * Check job status
     */
    private JobStatus checkJobStatus(String jobId) {
        try {
            String url = redashBaseUrl + "/api/jobs/" + jobId;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Failed to check job status: " + response.getStatusCode());
            }
            
            JsonNode jobNode = objectMapper.readTree(response.getBody());
            return JobStatus.fromJson(jobNode);
            
        } catch (Exception e) {
            log.error("Failed to check job status", e);
            throw new RuntimeException("Failed to check job status", e);
        }
    }
    
    /**
     * Get query results directly from Redash
     */
    private Object getQueryResults(Integer queryId) {
        try {
            String url = redashBaseUrl + "/api/queries/" + queryId + "/results";
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            // Use POST method as per your successful CLI test
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("Failed to get query results. Status: {}, Body: {}", 
                    response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to get query results: " + response.getStatusCode());
            }
            
            log.debug("Successfully got results from Redash for query ID: {}", queryId);
            
            // Parse and return the results
            JsonNode resultsNode = objectMapper.readTree(response.getBody());
            return convertRedashResultsToEsFormat(resultsNode);
            
        } catch (Exception e) {
            log.error("Failed to get query results for query ID: {}", queryId, e);
            throw new RuntimeException("Failed to get query results", e);
        }
    }
    
    /**
     * Convert Redash results format to match Elasticsearch response format
     */
    private Object convertRedashResultsToEsFormat(JsonNode redashResults) {
        try {
            // Extract the actual ES response from Redash wrapper
            JsonNode queryResultNode = redashResults.get("query_result");
            if (queryResultNode != null && queryResultNode.has("data")) {
                JsonNode dataNode = queryResultNode.get("data");
                if (dataNode.has("rows")) {
                    // This is tabular data, convert to ES hits format
                    return convertTabularToEsHits(dataNode);
                } else {
                    // This might be raw ES response
                    return dataNode;
                }
            }
            
            return redashResults;
            
        } catch (Exception e) {
            log.error("Failed to convert Redash results", e);
            return redashResults; // Return as-is if conversion fails
        }
    }
    
    /**
     * Convert tabular data to ES hits format
     */
    private Object convertTabularToEsHits(JsonNode dataNode) {
        // Implementation depends on your specific needs
        // For now, return the data node as-is
        return dataNode;
    }
    
    /**
     * Create HTTP headers with authorization
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Key " + apiKey);
        return headers;
    }
    
    /**
     * Get data source ID based on host type
     */
    private Integer getDataSourceId(String hostType) {
        switch (hostType.toLowerCase()) {
            case "primary":
                return primaryDataSourceId;
            case "secondary":
                return secondaryDataSourceId;
            case "tertiary":
                return tertiaryDataSourceId;
            default:
                log.warn("Unknown host type: {}, defaulting to primary", hostType);
                return primaryDataSourceId;
        }
    }
    
    /**
     * Inner class to represent job status
     */
    private static class JobStatus {
        private final int status;
        private final String error;
        
        private JobStatus(int status, String error) {
            this.status = status;
            this.error = error;
        }
        
        public boolean isCompleted() {
            return status == 3 || status == 4; // 3 = success, 4 = failed
        }
        
        public boolean isSuccess() {
            return status == 3;
        }
        
        public String getError() {
            return error;
        }
        
        public static JobStatus fromJson(JsonNode jobNode) {
            int status = jobNode.get("status").asInt();
            String error = jobNode.has("error") ? jobNode.get("error").asText() : null;
            return new JobStatus(status, error);
        }
    }
}

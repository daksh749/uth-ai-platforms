package com.paytm.mcpserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class RedashSearchService {
    
    @Value("${redash.base-url:http://10.84.84.143:5000}")
    private String redashBaseUrl;
    
    @Value("${redash.api-key}")
    private String apiKey;
    
    @Value("${redash.timeout:30000}")
    private long redashTimeout;
    
    @Value("${redash.poll-interval:2000}")
    private long redashPollInterval;
    
    @Value("${redash.max-poll-attempts:15}")
    private int redashMaxPollAttempts;
    
    @Value("${redash.connection-timeout:5000}")
    private int redashConnectionTimeout;
    
    @Value("${redash.read-timeout:30000}")
    private int redashReadTimeout;
    
    @Value("${redash.max-concurrent-searches:5}")
    private int redashMaxConcurrentSearches;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public RedashSearchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Execute search on multiple hosts and combine results
     */
    public String executeMultiHostSearch(String esQuery, List<String> indices, List<HostInfo> hosts) {
        log.info("Executing search on {} hosts with {} indices", hosts.size(), indices.size());
        
        try {
            // 1. Build complete query with indices
            String completeQuery = buildCompleteQuery(esQuery, indices);
            log.debug("Built complete query: {}", completeQuery);
            
            // 2. Execute search on all hosts
            List<HostResult> hostResults = new ArrayList<>();
            for (HostInfo host : hosts) {
                HostResult result = searchOnHost(completeQuery, host);
                hostResults.add(result);
            }
            
            // 3. Combine results from all hosts
            return combineResults(hostResults);
            
        } catch (Exception e) {
            log.error("Multi-host search failed", e);
            return createErrorResponse("Multi-host search failed: " + e.getMessage());
        }
    }
    
    /**
     * Build complete query by combining ES query with indices
     */
    private String buildCompleteQuery(String esQuery, List<String> indices) {
        try {
            JsonNode queryNode = objectMapper.readTree(esQuery);
            ObjectNode completeQuery = objectMapper.createObjectNode();
            
            // Add index
            completeQuery.put("index", String.join(",", indices));
            
            // Copy query parts
            if (queryNode.has("query")) {
                completeQuery.set("query", queryNode.get("query"));
            }
            if (queryNode.has("size")) {
                completeQuery.set("size", queryNode.get("size"));
            }
            if (queryNode.has("sort")) {
                completeQuery.set("sort", queryNode.get("sort"));
            }
            if (queryNode.has("aggs")) {
                completeQuery.set("aggs", queryNode.get("aggs"));
            }
            if (queryNode.has("_source")) {
                completeQuery.set("_source", queryNode.get("_source"));
            }
            
            return objectMapper.writeValueAsString(completeQuery);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to build complete query", e);
        }
    }
    
    /**
     * Execute search on a single host and return ES format data
     */
    private HostResult searchOnHost(String query, HostInfo host) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Searching on host: {} with dataSourceId: {}", host.hostName, host.dataSourceId);
            
            // Create Redash query
            Integer queryId = createRedashQuery(query, host.dataSourceId, host.hostName);
            
            // Execute and get raw results
            String rawResults = executeRedashQuery(queryId);
            
            // Convert Redash results to ES format
            Object esFormatResults = convertRedashResultsToEsFormat(rawResults);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Search completed on {} in {}ms", host.hostName, executionTime);
            
            return new HostResult(host.hostName, esFormatResults, null, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Search failed on {} after {}ms", host.hostName, executionTime, e);
            return new HostResult(host.hostName, null, e.getMessage(), executionTime);
        }
    }
    
    /**
     * Convert Redash wrapper to ES format data
     */
    private Object convertRedashResultsToEsFormat(String rawResults) {
        try {
            JsonNode redashResults = objectMapper.readTree(rawResults);
            
            // Extract the actual ES response from Redash wrapper
            JsonNode queryResultNode = redashResults.get("query_result");
            if (queryResultNode != null && queryResultNode.has("data")) {
                JsonNode dataNode = queryResultNode.get("data");
                return dataNode;
            }
            
            // Fallback to original if structure is different
            return redashResults;
            
        } catch (Exception e) {
            log.warn("Failed to convert Redash results to ES format", e);
            return rawResults; // Return raw string if parsing fails
        }
    }
    
    /**
     * Create query in Redash
     */
    private Integer createRedashQuery(String query, Integer dataSourceId, String hostName) {
        try {
            String url = redashBaseUrl + "/api/queries";
            
            Map<String, Object> requestBody = Map.of(
                "query", query,
                "data_source_id", dataSourceId,
                "name", "MCP-Search-" + hostName + "-" + System.currentTimeMillis()
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Key " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("id")) {
                Integer queryId = (Integer) responseBody.get("id");
                log.debug("Created Redash query with ID: {} for host: {}", queryId, hostName);
                return queryId;
            }
            
            throw new RuntimeException("Failed to create Redash query");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Redash query for " + hostName, e);
        }
    }
    
    /**
     * Execute Redash query and get results
     */
    private String executeRedashQuery(Integer queryId) {
        try {
            // Step 1: Trigger query execution
            String executeUrl = redashBaseUrl + "/api/queries/" + queryId + "/results";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Key " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> executeResponse = restTemplate.postForEntity(executeUrl, entity, String.class);
            
            if (executeResponse.getBody() == null) {
                throw new RuntimeException("Empty response from query execution");
            }
            
            // Step 2: Parse response - could be job or cached results
            JsonNode response = objectMapper.readTree(executeResponse.getBody());
            log.debug("Query execution response: {}", response);
            
            // Check if response has "job" (async) or "query_result" (cached)
            if (response.has("job")) {
                // Async execution - need to poll
                String jobId = response.get("job").get("id").asText();
                log.debug("Query execution started with job ID: {}", jobId);
                
                // Step 3: Poll for job completion
                Integer queryResultId = pollForJobCompletion(jobId);
                
                // Step 4: Fetch actual results
                String resultsUrl = redashBaseUrl + "/api/query_results/" + queryResultId;
                ResponseEntity<String> resultsResponse = restTemplate.exchange(
                    resultsUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class
                );
                
                if (resultsResponse.getBody() != null) {
                    log.debug("Successfully retrieved results for query result ID: {}", queryResultId);
                    return resultsResponse.getBody();
                }
                
                throw new RuntimeException("Empty response from Redash results fetch");
                
            } else if (response.has("query_result")) {
                // Cached results - return immediately
                log.debug("Query returned cached results");
                return executeResponse.getBody();
                
            } else {
                // Unexpected response format
                log.error("Unexpected Redash response format: {}", response);
                throw new RuntimeException("Unexpected response format from Redash: " + response);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Redash query", e);
        }
    }
    
    /**
     * Poll Redash job until completion
     */
    private Integer pollForJobCompletion(String jobId) {
        String jobUrl = redashBaseUrl + "/api/jobs/" + jobId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Key " + apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        int attempts = 0;
        while (attempts < redashMaxPollAttempts) {
            try {
                ResponseEntity<String> jobResponse = restTemplate.exchange(
                    jobUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class
                );
                
                JsonNode jobStatus = objectMapper.readTree(jobResponse.getBody());
                int status = jobStatus.get("job").get("status").asInt();
                
                // Status 3 = success
                if (status == 3) {
                    Integer queryResultId = jobStatus.get("job").get("query_result_id").asInt();
                    log.debug("Job {} completed successfully with result ID: {}", jobId, queryResultId);
                    return queryResultId;
                }
                
                // Status 4 = failure
                if (status == 4) {
                    String error = jobStatus.get("job").get("error").asText();
                    throw new RuntimeException("Redash job failed: " + error);
                }
                
                // Still processing (status 1 or 2), wait and retry
                Thread.sleep(redashPollInterval);
                attempts++;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Job polling interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to poll job status", e);
            }
        }
        
        throw new RuntimeException("Job polling timeout after " + attempts + " attempts");
    }
    
    /**
     * Combine ES format results from all hosts
     */
    private String combineResults(List<HostResult> hostResults) {
        try {
            ObjectNode combinedResponse = objectMapper.createObjectNode();
            ArrayNode allRows = objectMapper.createArrayNode();
            ArrayNode hostSummary = objectMapper.createArrayNode();
            
            int totalRows = 0;
            int successfulHosts = 0;
            List<String> errors = new ArrayList<>();
            long totalExecutionTime = 0;
            
            // Process each host result
            for (HostResult hostResult : hostResults) {
                totalExecutionTime += hostResult.executionTime;
                
                ObjectNode hostInfo = objectMapper.createObjectNode();
                hostInfo.put("host", hostResult.hostName);
                hostInfo.put("executionTimeMs", hostResult.executionTime);
                
                if (!hostResult.isSuccess()) {
                    // Host failed
                    hostInfo.put("status", "error");
                    hostInfo.put("error", hostResult.error);
                    errors.add(hostResult.hostName + ": " + hostResult.error);
                } else {
                    // Host succeeded - extract rows from ES data
                    try {
                        JsonNode esData = objectMapper.valueToTree(hostResult.results);
                        JsonNode rows = esData.get("rows");
                        
                        if (rows != null && rows.isArray()) {
                            int hostRowCount = 0;
                            for (JsonNode row : rows) {
                                ObjectNode rowWithHost = (ObjectNode) row.deepCopy();
                                rowWithHost.put("_source_host", hostResult.hostName);
                                allRows.add(rowWithHost);
                                hostRowCount++;
                            }
                            
                            totalRows += hostRowCount;
                            successfulHosts++;
                            hostInfo.put("status", "success");
                            hostInfo.put("rowCount", hostRowCount);
                        } else {
                            hostInfo.put("status", "success");
                            hostInfo.put("rowCount", 0);
                            successfulHosts++;
                        }
                    } catch (Exception e) {
                        hostInfo.put("status", "error");
                        hostInfo.put("error", "Failed to process ES data: " + e.getMessage());
                        errors.add(hostResult.hostName + ": Data processing error");
                    }
                }
                
                hostSummary.add(hostInfo);
            }
            
            // Sort results by txnDate (descending)
            sortResultsByDate(allRows);
            
            // Build final response in ES format
            ObjectNode dataNode = objectMapper.createObjectNode();
            dataNode.set("rows", allRows);
            dataNode.put("columns", extractColumnNames(allRows));
            
            ObjectNode queryResultNode = objectMapper.createObjectNode();
            queryResultNode.set("data", dataNode);
            queryResultNode.put("runtime", totalExecutionTime / 1000.0); // Convert to seconds
            
            // Add metadata
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("total_rows", totalRows);
            metadata.put("successful_hosts", successfulHosts);
            metadata.put("total_hosts", hostResults.size());
            metadata.put("execution_time_ms", totalExecutionTime);
            metadata.put("search_type", hostResults.size() > 1 ? "multi_host" : "single_host");
            
            combinedResponse.set("query_result", queryResultNode);
            combinedResponse.set("host_summary", hostSummary);
            combinedResponse.set("metadata", metadata);
            
            if (!errors.isEmpty()) {
                ArrayNode errorArray = objectMapper.createArrayNode();
                errors.forEach(errorArray::add);
                combinedResponse.set("errors", errorArray);
            }
            
            return objectMapper.writeValueAsString(combinedResponse);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to combine results", e);
        }
    }
    
    /**
     * Sort results by transaction date (descending)
     */
    private void sortResultsByDate(ArrayNode allRows) {
        List<JsonNode> sortedRows = new ArrayList<>();
        allRows.forEach(sortedRows::add);
        
        sortedRows.sort((a, b) -> {
            try {
                String dateA = a.has("txnDate") ? a.get("txnDate").asText() : "";
                String dateB = b.has("txnDate") ? b.get("txnDate").asText() : "";
                return dateB.compareTo(dateA); // Descending
            } catch (Exception e) {
                return 0;
            }
        });
        
        allRows.removeAll();
        sortedRows.forEach(allRows::add);
    }
    
    /**
     * Extract column names from results
     */
    private String extractColumnNames(ArrayNode rows) {
        if (rows.size() > 0) {
            JsonNode firstRow = rows.get(0);
            List<String> columns = new ArrayList<>();
            firstRow.fieldNames().forEachRemaining(columns::add);
            return String.join(",", columns);
        }
        return "No columns";
    }
    
    /**
     * Create error response
     */
    private String createErrorResponse(String message) {
        try {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", message);
            errorResponse.put("status", "error");
            errorResponse.put("timestamp", System.currentTimeMillis());
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            return "{\"error\":\"Failed to create error response\",\"status\":\"error\"}";
        }
    }
    
    // Helper classes
    public static class HostInfo {
        public final String hostName;
        public final Integer dataSourceId;
        
        public HostInfo(String hostName, Integer dataSourceId) {
            this.hostName = hostName;
            this.dataSourceId = dataSourceId;
        }
    }
    
    private static class HostResult {
        public final String hostName;
        public final Object results;  // Direct ES data object
        public final String error;
        public final long executionTime;

        public HostResult(String hostName, Object results, String error, long executionTime) {
            this.hostName = hostName;
            this.results = results;
            this.error = error;
            this.executionTime = executionTime;
        }
        
        public boolean isSuccess() {
            return error == null;
        }
        
    }
}
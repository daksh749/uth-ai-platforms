package com.paytm.mcpserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paytm.mcpserver.service.ElasticsearchHostSelector.HostCoverage;

import lombok.extern.log4j.Log4j2;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Log4j2
public class ElasticsearchService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    ElasticsearchSchemaFetcher elasticsearchSchemaFetcher;

    @Autowired
    ElasticsearchHostSelector elasticsearchHostSelector;

    @Autowired
    ElasticSearchIndexFetcher elasticSearchIndexFetcher;

    @Autowired
    private ElasticsearchQueryBuilderService queryBuilderService;

    @Autowired
    private RedashSearchService redashSearchService;

    @Tool(name="es_schema", description = "Bring the elastic search schema")
    public String fetchEsSchema(){
        return elasticsearchSchemaFetcher.fetchSchema();
    }

    @Tool(name="es_host", description = "get the es host to search upon based on start and end date")
    public String selectEsHost(@ToolParam(description = "Start date in DD-MM-YYYY format") String startDate,
                               @ToolParam(description = "End date in DD-MM-YYYY format") String endDate){
        try {
            List<HostCoverage> hostCoverages = elasticsearchHostSelector.selectHost(startDate, endDate);
            return objectMapper.writeValueAsString(hostCoverages);
        } catch (Exception e) {
            return String.format("{\"error\": \"Failed to serialize host coverage data\", \"message\": \"%s\"}", e.getMessage());
        }
    }

    @Tool(name = "es_indices", description = "get list of es indices to search upon")
    public String fetchEsIndices(@ToolParam(description = "Start date in DD-MM-YYYY format") String startDate,
                                  @ToolParam(description = "End date in DD-MM-YYYY format") String endDate){
        try {
            List<String> indices = elasticSearchIndexFetcher.findIndicesForDateRange(startDate, endDate);
            return objectMapper.writeValueAsString(indices);
        } catch (Exception e) {
            return String.format("{\"error\": \"Failed to serialize indices data\", \"message\": \"%s\"}", e.getMessage());
        }
    }

    @Tool(
            name = "es_query",
            description = "Convert natural language query to Elasticsearch DSL using LLM with schema context. Requires schema context from es_schema tool for proper field mappings."
    )
    public String buildElasticsearchQuery(
            @ToolParam(description = "Natural language query description") String prompt,
            @ToolParam(description = "Elasticsearch schema context (from es_schema tool)") String schemaContext,
            @ToolParam(description = "Maximum number of results to return (default 5 if not provided)", required = false) Integer maxResults,
            @ToolParam(description = "Include aggregations in query", required = false) Boolean includeAggregations,
            @ToolParam(description = "Sort field and order (e.g., 'txnDate:desc'). Defaults to 'txnDate:desc' if not provided", required = false) String sortBy
    ) {
        try {
            return queryBuilderService.buildQueryFromPrompt(prompt, schemaContext, maxResults, includeAggregations, sortBy);
        } catch (Exception e) {
            return String.format("""
                {
                  "error": "Failed to build Elasticsearch query",
                  "message": "%s",
                  "status": "error",
                  "hint": "Make sure to call es_schema tool first and pass the result as schemaContext parameter"
                }
                """, e.getMessage());
        }
    }

    @Tool(
            name = "es_search",
            description = "Execute Elasticsearch search via Redash across multiple hosts with result aggregation"
    )
    public String executeElasticsearchSearch(
            @ToolParam(description = "Elasticsearch query DSL JSON") String queryDsl,
            @ToolParam(description = "Host coverages JSON from es_host tool") String hostCoveragesJson,
            @ToolParam(description = "Comma-separated index names") String indices) {
        try {
            log.info("Executing Elasticsearch search with {} indices", indices);

            // Parse indices
            List<String> indexList = Arrays.asList(indices.split(","));

            // Parse host coverages - it's a direct array!
            JsonNode hostCoveragesArray = objectMapper.readTree(hostCoveragesJson);

            if (!hostCoveragesArray.isArray()) {
                throw new IllegalArgumentException("Expected array of host coverages");
            }

            // Build host info list
            List<RedashSearchService.HostInfo> hosts = new ArrayList<>();
            for (JsonNode coverage : hostCoveragesArray) {
                JsonNode hostNode = coverage.get("host");
                String hostName = hostNode.get("name").asText();
                Integer dataSourceId = hostNode.get("dataSourceId").asInt();
                hosts.add(new RedashSearchService.HostInfo(hostName, dataSourceId));
            }

            // Execute multi-host search
            return redashSearchService.executeMultiHostSearch(queryDsl, indexList, hosts);

        } catch (Exception e) {
            log.error("Failed to execute Elasticsearch search", e);
            return createErrorResponse("Failed to execute Elasticsearch search", e.getMessage());
        }
    }

    /**
     * Create standardized error response
     */
    private String createErrorResponse(String error, String message) {
        try {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", error);
            errorResponse.put("message", message);
            errorResponse.put("status", "error");
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            return "{\"error\":\"Failed to create error response\",\"status\":\"error\"}";
        }
    }

}

package com.paytm.mcpserver.mcp.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.shared.elasticsearch.model.EsHostType;
import com.paytm.shared.mcp.registry.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maps JSON-RPC parameters to tool-specific parameter formats
 * Handles conversion from simple JSON to complex objects like SearchSourceBuilder
 */
@Service
@Slf4j
public class ParameterMapper {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Map JSON-RPC parameters to tool-specific format
     * 
     * @param toolName Target tool name
     * @param jsonParams JSON-RPC parameters from client
     * @return Mapped parameters for tool execution
     */
    public Map<String, Object> mapParametersForTool(String toolName, Map<String, Object> jsonParams) {
        log.info("ParameterMapper - Mapping parameters for tool '{}': {}", toolName, jsonParams.keySet());
        
        // Log each incoming parameter
        for (Map.Entry<String, Object> entry : jsonParams.entrySet()) {
            Object value = entry.getValue();
            log.info("Incoming parameter '{}': type={}, value={}", 
                entry.getKey(), 
                value != null ? value.getClass().getSimpleName() : "null", 
                value);
        }
        
        switch (toolName) {
            case "es_search":
                return mapEsSearchParameters(jsonParams);
            case "es_schema":
                return mapEsSchemaParameters(jsonParams);
            case "es_host_search":
                return mapEsHostSearchParameters(jsonParams);
            default:
                log.debug("No specific mapping for tool '{}', using parameters as-is", toolName);
                return new HashMap<>(jsonParams);
        }
    }
    
    /**
     * Map parameters for es_search tool
     */
    private Map<String, Object> mapEsSearchParameters(Map<String, Object> jsonParams) {
        Map<String, Object> mappedParams = new HashMap<>();
        
        // Handle SearchSourceBuilder - can be provided directly or built from simple params
        SearchSourceBuilder searchSourceBuilder = null;
        
        if (jsonParams.containsKey("searchSourceBuilder")) {
            // Direct SearchSourceBuilder object (advanced usage)
            Object ssb = jsonParams.get("searchSourceBuilder");
            if (ssb instanceof SearchSourceBuilder) {
                searchSourceBuilder = (SearchSourceBuilder) ssb;
            } else if (ssb instanceof Map) {
                // Build from Map representation
                @SuppressWarnings("unchecked")
                Map<String, Object> ssbMap = (Map<String, Object>) ssb;
                searchSourceBuilder = buildSearchSourceBuilderFromMap(ssbMap);
            } else if (ssb instanceof String) {
                // Handle JSON string from client (most common case)
                log.info("Received SearchSourceBuilder as JSON string: {}", ssb);
                searchSourceBuilder = parseSearchSourceBuilderFromJson((String) ssb);
            } else {
                log.warn("SearchSourceBuilder parameter has unexpected type: {}, value: {}", 
                    ssb.getClass().getSimpleName(), ssb);
            }
        } else {
            // Build SearchSourceBuilder from simple parameters (common usage)
            searchSourceBuilder = buildSearchSourceBuilderFromSimpleParams(jsonParams);
        }
        
        if (searchSourceBuilder != null) {
            mappedParams.put("searchSourceBuilder", searchSourceBuilder);
            log.debug("Successfully mapped SearchSourceBuilder - hasQuery: {}, size: {}", 
                searchSourceBuilder.query() != null, searchSourceBuilder.size());
        } else {
            log.warn("SearchSourceBuilder is null after mapping!");
        }
        
        // Handle esHost parameter
        if (jsonParams.containsKey("esHost")) {
            Object hostObj = jsonParams.get("esHost");
            String hostStr = hostObj != null ? hostObj.toString() : "PRIMARY";
            try {
                EsHostType esHost = EsHostType.valueOf(hostStr.toUpperCase());
                mappedParams.put("esHost", esHost);
                log.debug("Mapped esHost: {} -> {}", hostStr, esHost);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid esHost value: {}. Using PRIMARY as default.", hostStr);
                mappedParams.put("esHost", EsHostType.PRIMARY);
            }
        } else {
            // Default to PRIMARY if not specified
            mappedParams.put("esHost", EsHostType.PRIMARY);
            log.debug("No esHost specified, defaulting to PRIMARY");
        }
        
        // Handle indices parameter
        if (jsonParams.containsKey("indices")) {
            Object indicesObj = jsonParams.get("indices");
            if (indicesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> indices = (List<String>) indicesObj;
                mappedParams.put("indices", indices);
            } else if (indicesObj instanceof String) {
                mappedParams.put("indices", Arrays.asList((String) indicesObj));
            }
        }
        
        log.debug("Mapped es_search parameters: {}", mappedParams.keySet());
        return mappedParams;
    }
    
    /**
     * Build SearchSourceBuilder from simple parameters (user-friendly)
     */
    private SearchSourceBuilder buildSearchSourceBuilderFromSimpleParams(Map<String, Object> params) {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        
        // Handle query parameter
        if (params.containsKey("query")) {
            String queryStr = (String) params.get("query");
            if (queryStr != null && !queryStr.trim().isEmpty()) {
                // Use multi_match query for searching across multiple fields
                builder.query(QueryBuilders.multiMatchQuery(queryStr, "_all", "searchFields.*"));
            }
        }
        
        // Handle size parameter
        if (params.containsKey("size")) {
            Object sizeObj = params.get("size");
            if (sizeObj instanceof Integer) {
                builder.size((Integer) sizeObj);
            } else if (sizeObj instanceof String) {
                try {
                    builder.size(Integer.parseInt((String) sizeObj));
                } catch (NumberFormatException e) {
                    log.warn("Invalid size parameter: {}. Using default.", sizeObj);
                }
            }
        }
        
        // Handle from parameter (pagination)
        if (params.containsKey("from")) {
            Object fromObj = params.get("from");
            if (fromObj instanceof Integer) {
                builder.from((Integer) fromObj);
            } else if (fromObj instanceof String) {
                try {
                    builder.from(Integer.parseInt((String) fromObj));
                } catch (NumberFormatException e) {
                    log.warn("Invalid from parameter: {}. Using default.", fromObj);
                }
            }
        }
        
        // Handle sort parameter
        if (params.containsKey("sort")) {
            Object sortObj = params.get("sort");
            if (sortObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sortMap = (Map<String, Object>) sortObj;
                for (Map.Entry<String, Object> entry : sortMap.entrySet()) {
                    String field = entry.getKey();
                    String order = entry.getValue().toString().toLowerCase();
                    SortOrder sortOrder = "desc".equals(order) ? SortOrder.DESC : SortOrder.ASC;
                    builder.sort(field, sortOrder);
                }
            } else if (sortObj instanceof String) {
                // Simple sort by field (ascending)
                builder.sort((String) sortObj, SortOrder.ASC);
            }
        }
        
        return builder;
    }
    
    /**
     * Build SearchSourceBuilder from Map representation (advanced)
     */
    private SearchSourceBuilder buildSearchSourceBuilderFromMap(Map<String, Object> ssbMap) {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        
        // This is a simplified implementation
        // In a full implementation, you'd handle all SearchSourceBuilder properties
        
        if (ssbMap.containsKey("size")) {
            builder.size(((Number) ssbMap.get("size")).intValue());
        }
        
        if (ssbMap.containsKey("from")) {
            builder.from(((Number) ssbMap.get("from")).intValue());
        }
        
        // Handle query object
        if (ssbMap.containsKey("query")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> queryMap = (Map<String, Object>) ssbMap.get("query");
            // Simplified query handling - in practice, you'd need full query DSL parsing
            if (queryMap.containsKey("match_all")) {
                builder.query(QueryBuilders.matchAllQuery());
            } else if (queryMap.containsKey("term")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> termMap = (Map<String, Object>) queryMap.get("term");
                for (Map.Entry<String, Object> entry : termMap.entrySet()) {
                    builder.query(QueryBuilders.termQuery(entry.getKey(), entry.getValue()));
                    break; // Just handle first term for simplicity
                }
            }
        }
        
        return builder;
    }
    
    /**
     * Parse SearchSourceBuilder from JSON string (from client)
     */
    private SearchSourceBuilder parseSearchSourceBuilderFromJson(String jsonString) {
        try {
            log.info("Parsing SearchSourceBuilder from JSON: {}", jsonString);
            
            // Parse JSON using Jackson
            JsonNode rootNode = objectMapper.readTree(jsonString);
            SearchSourceBuilder builder = new SearchSourceBuilder();
            
            log.info("JSON root node type: {}, isObject: {}", rootNode.getNodeType(), rootNode.isObject());
            
            if (rootNode.isObject()) {
                // List all available fields in the JSON
                java.util.List<String> fieldNames = new java.util.ArrayList<>();
                rootNode.fieldNames().forEachRemaining(fieldNames::add);
                log.info("Available JSON fields: {}", String.join(", ", fieldNames));
            }
            
            // Handle query
            if (rootNode.has("query")) {
                JsonNode queryNode = rootNode.get("query");
                log.info("Found query node: {}", queryNode.toString());
                org.elasticsearch.index.query.QueryBuilder queryBuilder = parseQueryFromJson(queryNode);
                builder.query(queryBuilder);
                log.info("Successfully set query builder: {}", queryBuilder.getClass().getSimpleName());
            } else {
                log.warn("No 'query' field found in JSON, using match_all as fallback");
                builder.query(QueryBuilders.matchAllQuery());
            }
            
            // Handle size
            if (rootNode.has("size")) {
                int size = rootNode.get("size").asInt();
                builder.size(size);
                log.info("Set size: {}", size);
            }
            
            // Handle from
            if (rootNode.has("from")) {
                int from = rootNode.get("from").asInt();
                builder.from(from);
                log.info("Set from: {}", from);
            }
            
            // Handle sort
            if (rootNode.has("sort")) {
                JsonNode sortNode = rootNode.get("sort");
                log.info("Found sort node: {}", sortNode.toString());
                if (sortNode.isArray()) {
                    for (JsonNode sortItem : sortNode) {
                        if (sortItem.isObject()) {
                            sortItem.fields().forEachRemaining(entry -> {
                                String field = entry.getKey();
                                JsonNode orderNode = entry.getValue();
                                SortOrder order = SortOrder.ASC;
                                if (orderNode.isObject() && orderNode.has("order")) {
                                    String orderStr = orderNode.get("order").asText();
                                    order = "desc".equalsIgnoreCase(orderStr) ? SortOrder.DESC : SortOrder.ASC;
                                }
                                builder.sort(field, order);
                                log.info("Added sort: {} {}", field, order);
                            });
                        }
                    }
                }
            }
            
            log.info("Successfully parsed SearchSourceBuilder - hasQuery: {}, size: {}", 
                builder.query() != null, builder.size());
            return builder;
            
        } catch (Exception e) {
            log.error("Failed to parse SearchSourceBuilder from JSON: {}", jsonString, e);
            // Return a fallback query
            SearchSourceBuilder fallback = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(100);
            log.warn("Returning fallback SearchSourceBuilder");
            return fallback;
        }
    }
    
    /**
     * Parse query from JSON node
     */
    private org.elasticsearch.index.query.QueryBuilder parseQueryFromJson(JsonNode queryNode) {
        try {
            log.info("Parsing query from JSON node: {}", queryNode.toString());
            
            if (queryNode.has("match_all")) {
                log.info("Found match_all query");
                return QueryBuilders.matchAllQuery();
            }
            
            if (queryNode.has("term")) {
                log.info("Found term query");
                JsonNode termNode = queryNode.get("term");
                if (termNode.isObject()) {
                    String field = termNode.fieldNames().next();
                    JsonNode valueNode = termNode.get(field);
                    Object value = getNodeValue(valueNode);
                    log.info("Term query: field={}, value={}", field, value);
                    return QueryBuilders.termQuery(field, value);
                }
            }
            
            if (queryNode.has("bool")) {
                log.info("Found bool query");
                JsonNode boolNode = queryNode.get("bool");
                var boolQuery = QueryBuilders.boolQuery();
                
                if (boolNode.has("must")) {
                    JsonNode mustNode = boolNode.get("must");
                    log.info("Bool query has 'must' clause with {} items", 
                        mustNode.isArray() ? mustNode.size() : 1);
                    if (mustNode.isArray()) {
                        for (JsonNode mustItem : mustNode) {
                            boolQuery.must(parseQueryFromJson(mustItem));
                        }
                    }
                }
                
                if (boolNode.has("filter")) {
                    JsonNode filterNode = boolNode.get("filter");
                    log.info("Bool query has 'filter' clause with {} items", 
                        filterNode.isArray() ? filterNode.size() : 1);
                    if (filterNode.isArray()) {
                        for (JsonNode filterItem : filterNode) {
                            boolQuery.filter(parseQueryFromJson(filterItem));
                        }
                    }
                }
                
                return boolQuery;
            }
            
            if (queryNode.has("range")) {
                log.info("Found range query");
                JsonNode rangeNode = queryNode.get("range");
                if (rangeNode.isObject()) {
                    String field = rangeNode.fieldNames().next();
                    JsonNode rangeParams = rangeNode.get(field);
                    var rangeQuery = QueryBuilders.rangeQuery(field);
                    
                    if (rangeParams.has("gte")) {
                        Object gteValue = getNodeValue(rangeParams.get("gte"));
                        rangeQuery.gte(gteValue);
                        log.info("Range query gte: {}", gteValue);
                    }
                    if (rangeParams.has("lte")) {
                        Object lteValue = getNodeValue(rangeParams.get("lte"));
                        rangeQuery.lte(lteValue);
                        log.info("Range query lte: {}", lteValue);
                    }
                    if (rangeParams.has("gt")) {
                        Object gtValue = getNodeValue(rangeParams.get("gt"));
                        rangeQuery.gt(gtValue);
                        log.info("Range query gt: {}", gtValue);
                    }
                    if (rangeParams.has("lt")) {
                        Object ltValue = getNodeValue(rangeParams.get("lt"));
                        rangeQuery.lt(ltValue);
                        log.info("Range query lt: {}", ltValue);
                    }
                    
                    return rangeQuery;
                }
            }
            
            log.warn("Unknown query type in JSON node: {}", queryNode.toString());
            
        } catch (Exception e) {
            log.error("Failed to parse query from JSON node", e);
        }
        
        // Fallback
        log.info("Using fallback match_all query");
        return QueryBuilders.matchAllQuery();
    }
    
    /**
     * Extract value from JSON node with proper type conversion
     */
    private Object getNodeValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            if (node.isInt()) {
                return node.asInt();
            } else if (node.isLong()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }
    
    
    /**
     * Map parameters for es_schema tool (no parameters needed)
     */
    private Map<String, Object> mapEsSchemaParameters(Map<String, Object> jsonParams) {
        // es_schema tool doesn't need parameters, but pass through any provided
        return new HashMap<>(jsonParams);
    }
    
    /**
     * Map parameters for es_host_search tool
     */
    private Map<String, Object> mapEsHostSearchParameters(Map<String, Object> jsonParams) {
        Map<String, Object> mappedParams = new HashMap<>();
        
        // Pass through date parameters as strings (tool handles parsing)
        if (jsonParams.containsKey("startDate")) {
            mappedParams.put("startDate", jsonParams.get("startDate").toString());
        }
        
        if (jsonParams.containsKey("endDate")) {
            mappedParams.put("endDate", jsonParams.get("endDate").toString());
        }
        
        return mappedParams;
    }
    
    /**
     * Validate parameters for a specific tool
     */
    public boolean validateParameters(String toolName, Map<String, Object> parameters, ToolMetadata toolMetadata) {
        if (toolMetadata == null) {
            log.warn("No metadata available for tool: {}", toolName);
            return false;
        }
        
        // Check required parameters are present
        List<String> required = toolMetadata.getRequiredParameters();
        if (required != null) {
            for (String requiredParam : required) {
                if (!parameters.containsKey(requiredParam)) {
                    log.warn("Missing required parameter '{}' for tool '{}'", requiredParam, toolName);
                    return false;
                }
            }
        }
        
        log.debug("Parameter validation passed for tool '{}'", toolName);
        return true;
    }
}

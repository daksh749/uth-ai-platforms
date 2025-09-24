package com.paytm.mcpclient.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryIntent {
    
    private String type;
    private Map<String, Object> filters;
    private TimeRange timeRange;
    private List<String> aggregations;
    private List<String> sorting;
    private String esHost;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeRange {
        private String from;
        private String to;
        private String field;
    }
    
    public enum QueryType {
        SEARCH("search"),
        AGGREGATION("aggregation"),
        COUNT("count"),
        ANALYTICS("analytics");
        
        private final String value;
        
        QueryType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static QueryType fromString(String value) {
            for (QueryType type : QueryType.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return SEARCH; // default
        }
    }
}

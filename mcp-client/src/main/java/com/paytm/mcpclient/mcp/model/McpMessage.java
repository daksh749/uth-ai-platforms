package com.paytm.mcpclient.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpMessage {
    
    @Builder.Default
    private String jsonrpc = "2.0";
    private String id;
    private String method;
    private Map<String, Object> params;
    private Object result;
    private McpError error;
    
    // Static factory methods for common message types
    public static McpMessage request(String id, String method, Map<String, Object> params) {
        return McpMessage.builder()
                .jsonrpc("2.0")
                .id(id)
                .method(method)
                .params(params)
                .build();
    }
    
    public static McpMessage response(String id, Object result) {
        return McpMessage.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(result)
                .build();
    }
    
    public static McpMessage error(String id, McpError error) {
        return McpMessage.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(error)
                .build();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpError {
        private int code;
        private String message;
        private Object data;
    }
}

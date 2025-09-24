package com.paytm.mcpserver.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConfigurationProperties(prefix = "mcp")
@Component
@Data
public class McpServerProperties {
    private ServerConfig server;
    private TransportConfig transport;
    private ToolsConfig tools;
    
    @Data
    public static class ServerConfig {
        private String name;
        private String version;
        private String description;
    }
    
    @Data
    public static class TransportConfig {
        private SseConfig sse;
        
        @Data
        public static class SseConfig {
            private String endpoint;
            private int heartbeatInterval;
            private int maxConnections;
        }
    }
    
    @Data
    public static class ToolsConfig {
        private List<String> enabled;
    }
}

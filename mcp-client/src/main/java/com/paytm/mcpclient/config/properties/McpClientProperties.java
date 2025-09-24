package com.paytm.mcpclient.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mcp")
public class McpClientProperties {
    
    private Server server = new Server();
    
    @Data
    public static class Server {
        private String url = "http://localhost:8080/api/mcp";
        private int timeout = 30000;
        private int retryAttempts = 3;
        private long heartbeatInterval = 30000;
    }
}

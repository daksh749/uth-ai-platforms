package com.paytm.mcpserver.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConfigurationProperties(prefix = "elasticsearch")
@Component
@Data
public class ElasticsearchProperties {
    private List<HostConfig> hosts;
    private ConnectionConfig connection;
    private QueryConfig query;
    
    @Data
    public static class HostConfig {
        private String name;
        private String url;
        private String username;
        private String password;
        private int timeout;
    }
    
    @Data
    public static class ConnectionConfig {
        private int poolSize;
        private boolean keepAlive;
        private int socketTimeout;
        private int connectionTimeout;
    }
    
    @Data
    public static class QueryConfig {
        private int defaultSize;
        private int maxSize;
        private String timeout;
        private String scrollTimeout;
    }
}

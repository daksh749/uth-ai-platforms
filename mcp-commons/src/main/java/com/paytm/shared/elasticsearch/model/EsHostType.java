package com.paytm.shared.elasticsearch.model;

/**
 * Enum representing different Elasticsearch host types for routing queries
 */
public enum EsHostType {
    PRIMARY("primary", "Primary Elasticsearch cluster for recent data"),
    SECONDARY("secondary", "Secondary Elasticsearch cluster for backup"),
    TERTIARY("tertiary", "Tertiary Elasticsearch cluster for historical data");
    
    private final String hostName;
    private final String description;
    
    EsHostType(String hostName, String description) {
        this.hostName = hostName;
        this.description = description;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public String getName() {
        return hostName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get host URL from configuration based on host type
     * This is a generic method that works with any configuration object that has the right structure
     * 
     * @param properties Configuration properties object with getHosts() method
     * @return Host URL for this host type
     * @throws IllegalStateException if host is not configured
     */
    public String getHostUrl(Object properties) {
        try {
            // Use reflection to call getHosts() method on the properties object
            java.lang.reflect.Method getHostsMethod = properties.getClass().getMethod("getHosts");
            @SuppressWarnings("unchecked")
            java.util.List<Object> hosts = (java.util.List<Object>) getHostsMethod.invoke(properties);
            
            for (Object host : hosts) {
                java.lang.reflect.Method getNameMethod = host.getClass().getMethod("getName");
                String hostName = (String) getNameMethod.invoke(host);
                
                if (hostName.equals(this.hostName)) {
                    java.lang.reflect.Method getUrlMethod = host.getClass().getMethod("getUrl");
                    return (String) getUrlMethod.invoke(host);
                }
            }
            
            throw new IllegalStateException("Host not configured: " + this.hostName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get host URL for " + this.hostName + ": " + e.getMessage(), e);
        }
    }
}

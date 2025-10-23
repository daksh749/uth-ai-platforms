package com.paytm.mcpserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP Client Configuration
 * Provides RestTemplate bean with proper timeout configuration
 */
@Configuration
public class HttpClientConfig {

    @Value("${redash.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${redash.read-timeout:30000}")
    private int readTimeout;

    /**
     * RestTemplate bean with configured timeouts
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectionTimeout);
        factory.setReadTimeout(readTimeout);
        
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}

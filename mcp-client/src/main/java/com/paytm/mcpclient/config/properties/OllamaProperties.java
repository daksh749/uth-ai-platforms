package com.paytm.mcpclient.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    
    private String url = "http://localhost:11434";
    private String model = "codellama";
    private int timeout = 60000;
    private double temperature = 0.1;
    private int maxTokens = 1000;
}

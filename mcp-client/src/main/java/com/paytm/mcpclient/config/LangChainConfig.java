package com.paytm.mcpclient.config;

import com.paytm.mcpclient.config.properties.OllamaProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChainConfig {
    
    @Autowired
    private OllamaProperties ollamaProperties;
    
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Configuring Ollama Chat Model with URL: {} and model: {}", 
                ollamaProperties.getUrl(), ollamaProperties.getModel());
        
        return OllamaChatModel.builder()
                .baseUrl(ollamaProperties.getUrl())
                .modelName(ollamaProperties.getModel())
                .temperature(ollamaProperties.getTemperature())
                .timeout(Duration.ofMillis(ollamaProperties.getTimeout()))  // Now 15 minutes from properties
                .build();
    }
}

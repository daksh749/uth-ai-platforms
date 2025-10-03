package com.paytm.mcpserver;

import com.paytm.mcpserver.service.ElasticsearchService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import lombok.extern.log4j.Log4j2;

@SpringBootApplication
@Log4j2
public class McpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpServerApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider elasticsearchTools(ElasticsearchService elasticsearchService){
		log.info("Registering Elasticsearch tools with MCP server");
		ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(elasticsearchService).build();
		log.info("Registered {} tools", provider.getToolCallbacks().length);
		return provider;
	}
}

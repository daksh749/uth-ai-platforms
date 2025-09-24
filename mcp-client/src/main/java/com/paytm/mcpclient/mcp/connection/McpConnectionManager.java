package com.paytm.mcpclient.mcp.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import com.paytm.mcpclient.config.properties.McpClientProperties;
import com.paytm.mcpclient.mcp.model.McpMessage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class McpConnectionManager {
    
    @Autowired
    private McpClientProperties mcpProperties;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final AtomicReference<String> connectionId = new AtomicReference<>();
    private final Map<String, CompletableFuture<McpMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final OkHttpClient httpClient;
    private EventSource eventSource;
    private volatile boolean connected = false;
    
    public McpConnectionManager() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(900000))  // 15 minutes
                .readTimeout(Duration.ofMillis(900000))     // 15 minutes
                .writeTimeout(Duration.ofMillis(900000))    // 15 minutes
                .build();
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing MCP Connection Manager");
        connect();
    }
    
    @PreDestroy
    public void cleanup() {
        disconnect();
        httpClient.dispatcher().executorService().shutdown();
    }
    
    public void connect() {
        if (connected) {
            log.warn("Already connected to MCP server");
            return;
        }
        
        try {
            String sseUrl = mcpProperties.getServer().getUrl() + "/sse";
            log.info("Connecting to MCP server via SSE: {}", sseUrl);
            
            EventSourceListener eventListener = new EventSourceListener() {
                @Override
                public void onOpen(EventSource eventSource, Response response) {
                    log.info("SSE connection opened to MCP server");
                    connected = true;
                }
                
                @Override
                public void onClosed(EventSource eventSource) {
                    log.info("SSE connection closed");
                    connected = false;
                    connectionId.set(null);
                }
                
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    handleSseMessage(type, data);
                }
                
                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    log.error("SSE connection error", t);
                    connected = false;
                }
            };
            
            Request request = new Request.Builder()
                    .url(sseUrl)
                    .build();
            
            eventSource = EventSources.createFactory(httpClient)
                    .newEventSource(request, eventListener);
            
        } catch (Exception e) {
            log.error("Failed to connect to MCP server", e);
            connected = false;
        }
    }
    
    public void disconnect() {
        if (eventSource != null) {
            eventSource.cancel();
            eventSource = null;
        }
        connected = false;
        connectionId.set(null);
        pendingRequests.clear();
    }
    
    public boolean isConnected() {
        return connected && connectionId.get() != null;
    }
    
    public String getConnectionId() {
        return connectionId.get();
    }
    
    public McpMessage sendMessage(McpMessage message) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MCP server");
        }
        log.info("connected to mcp-server");
        
        CompletableFuture<McpMessage> future = new CompletableFuture<>();
        pendingRequests.put(message.getId(), future);
        
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            String messageUrl = mcpProperties.getServer().getUrl() + "/message?connectionId=" + connectionId.get();
            
            RequestBody body = RequestBody.create(messageJson, MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(messageUrl)
                    .post(body)
                    .build();
            
            // Execute synchronously
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = "HTTP " + response.code() + ": " + response.message();
                    log.error("MCP message send failed: {}", error);
                    pendingRequests.remove(message.getId());
                    throw new IOException(error);
                }
                // Response will come via SSE, so we wait for it
            }
            
            // Block and wait for the SSE response with 30 second timeout
            return future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("MCP message timed out after 30 seconds", e);
            pendingRequests.remove(message.getId());
            throw new RuntimeException("MCP request timed out after 30 seconds", e);
        } catch (Exception e) {
            log.error("Error sending MCP message", e);
            pendingRequests.remove(message.getId());
            throw new RuntimeException("Failed to send MCP message: " + e.getMessage(), e);
        }
    }
    
    private void handleSseMessage(String event, String data) {
        try {
            log.debug("Received SSE event: {} with data: {}", event, data);
            
            if ("connection-id".equals(event)) {
                connectionId.set(data);
                log.info("Received connection ID: {}", data);
                return;
            }
            
            if ("mcp-response".equals(event)) {
                McpMessage response = objectMapper.readValue(data, McpMessage.class);
                String messageId = response.getId();
                
                CompletableFuture<McpMessage> future = pendingRequests.remove(messageId);
                if (future != null) {
                    future.complete(response);
                } else {
                    log.warn("Received response for unknown message ID: {}", messageId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling SSE message", e);
        }
    }
}

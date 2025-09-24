package com.paytm.mcpserver.transport.sse;

import com.paytm.mcpserver.mcp.protocol.McpProtocolHandler;
import com.paytm.shared.mcp.protocol.McpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * SSE Controller for MCP (Model Context Protocol) transport
 * Provides Server-Sent Events endpoint for real-time communication with MCP clients
 */
@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class SseController {
    
    @Autowired
    private SseConnectionManager connectionManager;
    
    @Autowired
    private McpProtocolHandler mcpProtocolHandler;
    
    /**
     * SSE endpoint for MCP client connections
     * GET /mcp/sse
     * 
     * @param clientId Optional client identifier for connection tracking
     * @return SseEmitter for streaming events to client
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse(@RequestParam(name = "clientId", required = false) String clientId) {
        log.info("New SSE connection request from client: {}", clientId);
        
        try {
            // Create SSE emitter with 30-minute timeout
            SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 minutes
            
            // Register connection
            String connectionId = connectionManager.registerConnection(emitter, clientId);
            
            // Send initial connection confirmation
            sendConnectionConfirmation(emitter, connectionId);
            
            // Set up connection cleanup handlers
            setupConnectionHandlers(emitter, connectionId);
            
            log.info("SSE connection established with ID: {}", connectionId);
            return emitter;
            
        } catch (Exception e) {
            log.error("Failed to establish SSE connection for client: {}", clientId, e);
            throw new RuntimeException("Failed to establish SSE connection", e);
        }
    }
    
    /**
     * Health check endpoint for SSE transport
     * GET /mcp/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "transport", "SSE",
            "timestamp", Instant.now().toString(),
            "active_connections", connectionManager.getActiveConnectionCount(),
            "total_connections", connectionManager.getTotalConnectionCount()
        );
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get connection statistics
     * GET /mcp/connections
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnections() {
        return ResponseEntity.ok(connectionManager.getConnectionStats());
    }
    
    /**
     * Handle MCP JSON-RPC messages
     * POST /mcp/message
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMcpMessage(
            @RequestBody String jsonMessage,
            @RequestParam(name = "connectionId") String connectionId) {
        
        log.info("Received MCP message from connection {}: {}", connectionId, jsonMessage);
        
        try {
            // Validate connection exists
            if (!connectionManager.hasConnection(connectionId)) {
                log.warn("Message received for unknown connection: {}", connectionId);
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Connection not found: " + connectionId
                ));
            }
            
            // Process JSON-RPC message
            McpResponse response = mcpProtocolHandler.handleMessage(jsonMessage, connectionId);
            
            // Send response back via SSE
            boolean sent = connectionManager.sendToConnection(connectionId, "mcp-response", response);
            
            if (sent) {
                log.debug("MCP response sent to connection {}", connectionId);
                return ResponseEntity.ok(Map.of(
                    "status", "processed",
                    "connection_id", connectionId,
                    "response_sent", true,
                    "timestamp", Instant.now().toString()
                ));
            } else {
                log.error("Failed to send MCP response to connection {}", connectionId);
                return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to send response to connection"
                ));
            }
            
        } catch (Exception e) {
            log.error("Failed to process MCP message from connection {}: {}", connectionId, jsonMessage, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to process message: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Send a test message to all connected clients (for testing purposes)
     * POST /mcp/broadcast
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody Map<String, Object> message) {
        log.info("Broadcasting test message to all clients: {}", message);
        
        int sentCount = connectionManager.broadcastToAll("test", message);
        
        Map<String, Object> response = Map.of(
            "status", "sent",
            "message", message,
            "recipients", sentCount,
            "timestamp", Instant.now().toString()
        );
        
        return ResponseEntity.ok(response);
    }
    
    private void sendConnectionConfirmation(SseEmitter emitter, String connectionId) {
        try {
            // Send connection ID as separate event that the client expects
            emitter.send(SseEmitter.event()
                .name("connection-id")  // Client expects "connection-id" event
                .data(connectionId)     // Send just the connection ID string
                .id(connectionId + "_id"));
            
            log.debug("Sent connection ID to client: {}", connectionId);
            
            // Send connection details as separate event for additional info
            Map<String, Object> confirmationData = Map.of(
                "type", "connection_established",
                "connection_id", connectionId,
                "timestamp", Instant.now().toString(),
                "server_info", Map.of(
                    "name", "UTH AI Systems MCP Server",
                    "version", "1.0.0",
                    "transport", "SSE"
                )
            );
            
            emitter.send(SseEmitter.event()
                .name("connection")
                .data(confirmationData)
                .id(connectionId + "_init"));
                
        } catch (IOException e) {
            log.error("Failed to send connection confirmation for: {}", connectionId, e);
        }
    }
    
    private void setupConnectionHandlers(SseEmitter emitter, String connectionId) {
        // Handle completion (normal close)
        emitter.onCompletion(() -> {
            log.info("SSE connection completed normally: {}", connectionId);
            connectionManager.removeConnection(connectionId);
        });
        
        // Handle timeout
        emitter.onTimeout(() -> {
            log.warn("SSE connection timed out: {}", connectionId);
            connectionManager.removeConnection(connectionId);
        });
        
        // Handle errors
        emitter.onError((throwable) -> {
            log.error("SSE connection error for: {}", connectionId, throwable);
            connectionManager.removeConnection(connectionId);
        });
    }
}

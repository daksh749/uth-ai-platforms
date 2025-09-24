package com.paytm.mcpserver.transport.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages SSE connections for MCP transport
 * Handles connection registration, removal, and message broadcasting
 */
@Service
@Slf4j
public class SseConnectionManager {
    
    private final Map<String, SseConnection> activeConnections = new ConcurrentHashMap<>();
    private final AtomicLong connectionCounter = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    
    /**
     * Register a new SSE connection
     * 
     * @param emitter SSE emitter for the connection
     * @param clientId Optional client identifier
     * @return Generated connection ID
     */
    public String registerConnection(SseEmitter emitter, String clientId) {
        String connectionId = generateConnectionId(clientId);
        
        SseConnection connection = new SseConnection(
            connectionId,
            emitter,
            clientId,
            Instant.now()
        );
        
        activeConnections.put(connectionId, connection);
        totalConnections.incrementAndGet();
        
        log.info("Registered SSE connection: {} (client: {}, total active: {})", 
            connectionId, clientId, activeConnections.size());
        
        return connectionId;
    }
    
    /**
     * Remove an SSE connection
     * 
     * @param connectionId Connection ID to remove
     */
    public void removeConnection(String connectionId) {
        SseConnection connection = activeConnections.remove(connectionId);
        
        if (connection != null) {
            try {
                connection.getEmitter().complete();
            } catch (Exception e) {
                log.debug("Error completing emitter for connection: {}", connectionId, e);
            }
            
            log.info("Removed SSE connection: {} (client: {}, remaining active: {})", 
                connectionId, connection.getClientId(), activeConnections.size());
        }
    }
    
    /**
     * Send message to a specific connection
     * 
     * @param connectionId Target connection ID
     * @param eventName Event name
     * @param data Event data
     * @return true if message was sent successfully
     */
    public boolean sendToConnection(String connectionId, String eventName, Object data) {
        log.debug("Attempting to send message to connection {}: {}", connectionId, eventName);
        log.debug("Current active connections: {}", activeConnections.keySet());
        
        SseConnection connection = activeConnections.get(connectionId);
        
        if (connection == null) {
            log.warn("Connection not found: {}. Active connections: {}", connectionId, activeConnections.keySet());
            return false;
        }
        
        try {
            log.debug("Sending SSE event to connection {}: event={}, dataType={}", 
                connectionId, eventName, data != null ? data.getClass().getSimpleName() : "null");
            
            connection.getEmitter().send(SseEmitter.event()
                .name(eventName)
                .data(data)
                .id(connectionId + "_" + System.currentTimeMillis()));
            
            connection.incrementMessageCount();
            log.debug("Successfully sent message to connection {}: {}", connectionId, eventName);
            return true;
            
        } catch (IOException e) {
            log.error("IOException while sending message to connection {}: {}", connectionId, e.getMessage(), e);
            log.warn("Removing broken connection: {}", connectionId);
            removeConnection(connectionId);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error sending message to connection {}: {}", connectionId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Broadcast message to all active connections
     * 
     * @param eventName Event name
     * @param data Event data
     * @return Number of connections that received the message
     */
    public int broadcastToAll(String eventName, Object data) {
        int successCount = 0;
        
        for (String connectionId : activeConnections.keySet()) {
            if (sendToConnection(connectionId, eventName, data)) {
                successCount++;
            }
        }
        
        log.debug("Broadcasted message '{}' to {}/{} connections", 
            eventName, successCount, activeConnections.size());
        
        return successCount;
    }
    
    /**
     * Broadcast message to connections matching client ID
     * 
     * @param clientId Target client ID
     * @param eventName Event name
     * @param data Event data
     * @return Number of connections that received the message
     */
    public int broadcastToClient(String clientId, String eventName, Object data) {
        int successCount = 0;
        
        for (SseConnection connection : activeConnections.values()) {
            if (clientId.equals(connection.getClientId())) {
                if (sendToConnection(connection.getConnectionId(), eventName, data)) {
                    successCount++;
                }
            }
        }
        
        log.debug("Broadcasted message '{}' to client '{}': {}/{} connections", 
            eventName, clientId, successCount, activeConnections.size());
        
        return successCount;
    }
    
    /**
     * Get current connection statistics
     * 
     * @return Connection statistics map
     */
    public Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("active_connections", activeConnections.size());
        stats.put("total_connections_created", totalConnections.get());
        stats.put("timestamp", Instant.now().toString());
        
        // Connection details
        Map<String, Object> connections = new ConcurrentHashMap<>();
        activeConnections.forEach((id, conn) -> {
            connections.put(id, Map.of(
                "client_id", conn.getClientId() != null ? conn.getClientId() : "anonymous",
                "connected_at", conn.getConnectedAt().toString(),
                "message_count", conn.getMessageCount()
            ));
        });
        stats.put("connections", connections);
        
        return stats;
    }
    
    /**
     * Get number of active connections
     * 
     * @return Active connection count
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    /**
     * Get total number of connections created
     * 
     * @return Total connection count
     */
    public long getTotalConnectionCount() {
        return totalConnections.get();
    }
    
    /**
     * Check if a connection exists
     * 
     * @param connectionId Connection ID to check
     * @return true if connection exists
     */
    public boolean hasConnection(String connectionId) {
        return activeConnections.containsKey(connectionId);
    }
    
    /**
     * Clean up all connections (for shutdown)
     */
    public void shutdown() {
        log.info("Shutting down SSE connection manager, closing {} connections", 
            activeConnections.size());
        
        activeConnections.keySet().forEach(this::removeConnection);
        activeConnections.clear();
    }
    
    private String generateConnectionId(String clientId) {
        long counter = connectionCounter.incrementAndGet();
        String prefix = clientId != null ? clientId : "client";
        return String.format("%s_%d_%d", prefix, System.currentTimeMillis(), counter);
    }
    
    /**
     * Inner class representing an SSE connection
     */
    private static class SseConnection {
        private final String connectionId;
        private final SseEmitter emitter;
        private final String clientId;
        private final Instant connectedAt;
        private final AtomicLong messageCount;
        
        public SseConnection(String connectionId, SseEmitter emitter, String clientId, Instant connectedAt) {
            this.connectionId = connectionId;
            this.emitter = emitter;
            this.clientId = clientId;
            this.connectedAt = connectedAt;
            this.messageCount = new AtomicLong(0);
        }
        
        public String getConnectionId() {
            return connectionId;
        }
        
        public SseEmitter getEmitter() {
            return emitter;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public Instant getConnectedAt() {
            return connectedAt;
        }
        
        public long getMessageCount() {
            return messageCount.get();
        }
        
        public void incrementMessageCount() {
            messageCount.incrementAndGet();
        }
    }
}

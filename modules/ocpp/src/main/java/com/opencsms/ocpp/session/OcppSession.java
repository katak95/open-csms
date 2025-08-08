package com.opencsms.ocpp.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an OCPP WebSocket session with a charging station.
 */
@Getter
@Setter
@Slf4j
public class OcppSession {

    private final String sessionId;
    private final String stationId;
    private final String tenantId;
    private final String ocppVersion;
    private final String clientIp;
    private final WebSocketSession webSocketSession;
    private final Instant connectedAt;
    private final AtomicLong messageCounter = new AtomicLong(0);
    
    private Instant lastHeartbeat;
    private Instant lastMessageSent;
    private Instant lastMessageReceived;
    private boolean authenticated = false;
    private String bootNotificationStatus = "Pending";
    
    // Pending messages waiting for responses (messageId -> message)
    private final ConcurrentHashMap<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    
    // Session metadata
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    public OcppSession(WebSocketSession webSocketSession, String stationId, 
                      String tenantId, String ocppVersion, String clientIp) {
        this.sessionId = webSocketSession.getId();
        this.webSocketSession = webSocketSession;
        this.stationId = stationId;
        this.tenantId = tenantId;
        this.ocppVersion = ocppVersion;
        this.clientIp = clientIp;
        this.connectedAt = Instant.now();
        this.lastHeartbeat = Instant.now();
    }

    /**
     * Record that a message was sent to the station.
     */
    public void recordMessageSent() {
        this.lastMessageSent = Instant.now();
        this.messageCounter.incrementAndGet();
    }

    /**
     * Record that a message was received from the station.
     */
    public void recordMessageReceived() {
        this.lastMessageReceived = Instant.now();
        this.messageCounter.incrementAndGet();
    }

    /**
     * Update heartbeat timestamp.
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
        log.debug("Updated heartbeat for station: {}", stationId);
    }

    /**
     * Add a pending message waiting for response.
     */
    public void addPendingMessage(String messageId, PendingMessage message) {
        pendingMessages.put(messageId, message);
        log.debug("Added pending message {} for station: {}", messageId, stationId);
    }

    /**
     * Remove and return a pending message.
     */
    public PendingMessage removePendingMessage(String messageId) {
        PendingMessage message = pendingMessages.remove(messageId);
        if (message != null) {
            log.debug("Removed pending message {} for station: {}", messageId, stationId);
        }
        return message;
    }

    /**
     * Check if session has expired heartbeat.
     */
    public boolean hasExpiredHeartbeat(int timeoutSeconds) {
        return lastHeartbeat.isBefore(Instant.now().minusSeconds(timeoutSeconds));
    }

    /**
     * Get session duration in seconds.
     */
    public long getSessionDurationSeconds() {
        return java.time.Duration.between(connectedAt, Instant.now()).getSeconds();
    }

    /**
     * Check if the WebSocket session is still open.
     */
    public boolean isOpen() {
        return webSocketSession.isOpen();
    }

    /**
     * Set session attribute.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Get session attribute.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Remove session attribute.
     */
    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }

    @Override
    public String toString() {
        return String.format("OcppSession{stationId='%s', tenantId='%s', version='%s', " +
                           "connected=%s, authenticated=%s, messageCount=%d}", 
                           stationId, tenantId, ocppVersion, connectedAt, authenticated, 
                           messageCounter.get());
    }

    /**
     * Represents a message waiting for a response.
     */
    @Getter
    @Setter
    public static class PendingMessage {
        private final String messageId;
        private final String action;
        private final String payload;
        private final Instant sentAt;
        private int retryCount = 0;
        private Instant lastRetryAt;

        public PendingMessage(String messageId, String action, String payload) {
            this.messageId = messageId;
            this.action = action;
            this.payload = payload;
            this.sentAt = Instant.now();
        }

        public void incrementRetryCount() {
            this.retryCount++;
            this.lastRetryAt = Instant.now();
        }

        public boolean hasExpired(int timeoutSeconds) {
            return sentAt.isBefore(Instant.now().minusSeconds(timeoutSeconds));
        }

        @Override
        public String toString() {
            return String.format("PendingMessage{id='%s', action='%s', sent=%s, retries=%d}", 
                               messageId, action, sentAt, retryCount);
        }
    }
}
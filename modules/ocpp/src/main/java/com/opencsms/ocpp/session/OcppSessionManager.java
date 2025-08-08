package com.opencsms.ocpp.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages OCPP WebSocket sessions for charging stations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcppSessionManager {

    // Map of session ID to OCPP session
    private final Map<String, OcppSession> sessions = new ConcurrentHashMap<>();
    
    // Map of station ID to session ID for quick lookup
    private final Map<String, String> stationToSession = new ConcurrentHashMap<>();

    /**
     * Register a new OCPP session.
     */
    public OcppSession registerSession(WebSocketSession webSocketSession, String stationId,
                                     String tenantId, String ocppVersion, String clientIp) {
        String sessionId = webSocketSession.getId();
        
        // Remove any existing session for this station
        removeSessionByStationId(stationId);
        
        OcppSession ocppSession = new OcppSession(webSocketSession, stationId, 
                                                tenantId, ocppVersion, clientIp);
        
        sessions.put(sessionId, ocppSession);
        stationToSession.put(getStationKey(stationId, tenantId), sessionId);
        
        log.info("Registered OCPP session: {} for station: {} (tenant: {})", 
                sessionId, stationId, tenantId);
        
        return ocppSession;
    }

    /**
     * Remove a session by session ID.
     */
    public void removeSession(String sessionId) {
        OcppSession session = sessions.remove(sessionId);
        if (session != null) {
            String stationKey = getStationKey(session.getStationId(), session.getTenantId());
            stationToSession.remove(stationKey);
            
            log.info("Removed OCPP session: {} for station: {} (tenant: {})", 
                    sessionId, session.getStationId(), session.getTenantId());
        }
    }

    /**
     * Remove session by station ID and tenant.
     */
    public void removeSessionByStationId(String stationId, String tenantId) {
        String stationKey = getStationKey(stationId, tenantId);
        String sessionId = stationToSession.remove(stationKey);
        if (sessionId != null) {
            OcppSession session = sessions.remove(sessionId);
            if (session != null) {
                log.info("Removed OCPP session by station ID: {} (tenant: {})", stationId, tenantId);
            }
        }
    }

    /**
     * Remove session by station ID (for backward compatibility).
     */
    private void removeSessionByStationId(String stationId) {
        // Find and remove sessions for this station across all tenants
        List<Map.Entry<String, String>> toRemove = stationToSession.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(stationId + ":"))
            .collect(Collectors.toList());
        
        for (Map.Entry<String, String> entry : toRemove) {
            String sessionId = entry.getValue();
            OcppSession session = sessions.remove(sessionId);
            stationToSession.remove(entry.getKey());
            if (session != null) {
                log.info("Removed existing OCPP session for station: {}", stationId);
            }
        }
    }

    /**
     * Get session by session ID.
     */
    public Optional<OcppSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Get session by station ID and tenant.
     */
    public Optional<OcppSession> getSessionByStationId(String stationId, String tenantId) {
        String stationKey = getStationKey(stationId, tenantId);
        String sessionId = stationToSession.get(stationKey);
        return sessionId != null ? getSession(sessionId) : Optional.empty();
    }

    /**
     * Get all active sessions.
     */
    public List<OcppSession> getAllSessions() {
        return List.copyOf(sessions.values());
    }

    /**
     * Get sessions for a specific tenant.
     */
    public List<OcppSession> getSessionsByTenant(String tenantId) {
        return sessions.values().stream()
            .filter(session -> tenantId.equals(session.getTenantId()))
            .collect(Collectors.toList());
    }

    /**
     * Get sessions by OCPP version.
     */
    public List<OcppSession> getSessionsByVersion(String ocppVersion) {
        return sessions.values().stream()
            .filter(session -> ocppVersion.equals(session.getOcppVersion()))
            .collect(Collectors.toList());
    }

    /**
     * Check if a station is currently connected.
     */
    public boolean isStationConnected(String stationId, String tenantId) {
        return getSessionByStationId(stationId, tenantId)
            .map(OcppSession::isOpen)
            .orElse(false);
    }

    /**
     * Get the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Get sessions with expired heartbeats.
     */
    public List<OcppSession> getSessionsWithExpiredHeartbeat(int timeoutSeconds) {
        return sessions.values().stream()
            .filter(session -> session.hasExpiredHeartbeat(timeoutSeconds))
            .collect(Collectors.toList());
    }

    /**
     * Update heartbeat for a session.
     */
    public void updateHeartbeat(String sessionId) {
        OcppSession session = sessions.get(sessionId);
        if (session != null) {
            session.updateHeartbeat();
        }
    }

    /**
     * Scheduled task to clean up expired sessions and pending messages.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupExpiredSessions() {
        int initialCount = sessions.size();
        
        // Remove closed sessions
        List<String> closedSessions = sessions.entrySet().stream()
            .filter(entry -> !entry.getValue().isOpen())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        for (String sessionId : closedSessions) {
            removeSession(sessionId);
        }
        
        // Clean up expired pending messages
        int expiredMessages = 0;
        for (OcppSession session : sessions.values()) {
            List<String> expiredMessageIds = session.getPendingMessages().entrySet().stream()
                .filter(entry -> entry.getValue().hasExpired(300)) // 5 minutes timeout
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            for (String messageId : expiredMessageIds) {
                session.removePendingMessage(messageId);
                expiredMessages++;
            }
        }
        
        if (closedSessions.size() > 0 || expiredMessages > 0) {
            log.info("Cleaned up {} closed sessions and {} expired messages. " +
                    "Active sessions: {} -> {}", 
                    closedSessions.size(), expiredMessages, initialCount, sessions.size());
        }
    }

    /**
     * Get session statistics.
     */
    public SessionStatistics getStatistics() {
        Map<String, Long> versionCounts = sessions.values().stream()
            .collect(Collectors.groupingBy(
                OcppSession::getOcppVersion, 
                Collectors.counting()
            ));
        
        Map<String, Long> tenantCounts = sessions.values().stream()
            .collect(Collectors.groupingBy(
                OcppSession::getTenantId, 
                Collectors.counting()
            ));
        
        long totalMessages = sessions.values().stream()
            .mapToLong(session -> session.getMessageCounter().get())
            .sum();
        
        int totalPendingMessages = sessions.values().stream()
            .mapToInt(session -> session.getPendingMessages().size())
            .sum();
        
        return SessionStatistics.builder()
            .totalSessions(sessions.size())
            .versionCounts(versionCounts)
            .tenantCounts(tenantCounts)
            .totalMessages(totalMessages)
            .totalPendingMessages(totalPendingMessages)
            .build();
    }

    private String getStationKey(String stationId, String tenantId) {
        return stationId + ":" + tenantId;
    }

    /**
     * Statistics about active sessions.
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionStatistics {
        private int totalSessions;
        private Map<String, Long> versionCounts;
        private Map<String, Long> tenantCounts;
        private long totalMessages;
        private int totalPendingMessages;
    }
}
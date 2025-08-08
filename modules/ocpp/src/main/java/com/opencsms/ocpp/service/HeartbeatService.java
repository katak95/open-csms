package com.opencsms.ocpp.service;

import com.opencsms.ocpp.message.ParsedOcppMessage;
import com.opencsms.ocpp.message.v16.response.HeartbeatResponse;
import com.opencsms.ocpp.session.OcppSession;
import com.opencsms.ocpp.session.OcppSessionManager;
import com.opencsms.service.station.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for handling OCPP Heartbeat messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private final StationService stationService;
    private final OcppSessionManager sessionManager;

    /**
     * Process Heartbeat message from charging station.
     */
    public Object handleHeartbeat(OcppSession session, ParsedOcppMessage message) {
        String stationId = session.getStationId();
        String tenantId = session.getTenantId();
        String ocppVersion = session.getOcppVersion();
        
        log.debug("Processing Heartbeat from station: {} (tenant: {}, version: {})", 
                stationId, tenantId, ocppVersion);
        
        try {
            // Update session heartbeat
            session.updateHeartbeat();
            sessionManager.updateHeartbeat(session.getSessionId());
            
            // Update station heartbeat in database
            stationService.updateHeartbeat(stationId);
            
            // Create response with current time
            Instant currentTime = Instant.now();
            
            if (ocppVersion.startsWith("1.6")) {
                return new HeartbeatResponse(currentTime);
            } else if (ocppVersion.startsWith("2.0")) {
                // OCPP 2.0.1 also uses same response format
                return new com.opencsms.ocpp.message.v201.response.HeartbeatResponse(currentTime);
            } else {
                log.warn("Unsupported OCPP version for Heartbeat: {}", ocppVersion);
                return new HeartbeatResponse(currentTime); // Fallback to 1.6 format
            }
            
        } catch (Exception e) {
            log.error("Error processing Heartbeat for station: {}", stationId, e);
            // Even if there's an error, we should respond with current time
            return new HeartbeatResponse(Instant.now());
        }
    }

    /**
     * Check for stations with expired heartbeats and mark them as disconnected.
     */
    public void checkExpiredHeartbeats() {
        try {
            // Get sessions with expired heartbeat (default 10 minutes timeout)
            var expiredSessions = sessionManager.getSessionsWithExpiredHeartbeat(600);
            
            for (OcppSession session : expiredSessions) {
                String stationId = session.getStationId();
                log.warn("Station {} heartbeat expired, marking as disconnected", stationId);
                
                try {
                    // Mark station as disconnected
                    stationService.markDisconnected(stationId);
                    
                    // Close the session
                    if (session.isOpen()) {
                        session.getWebSocketSession().close();
                    }
                    
                    // Remove from session manager
                    sessionManager.removeSession(session.getSessionId());
                    
                } catch (Exception e) {
                    log.error("Error handling expired heartbeat for station: {}", stationId, e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking expired heartbeats", e);
        }
    }

    /**
     * Get heartbeat statistics for monitoring.
     */
    public HeartbeatStatistics getHeartbeatStatistics() {
        try {
            var allSessions = sessionManager.getAllSessions();
            var expiredSessions = sessionManager.getSessionsWithExpiredHeartbeat(600);
            
            long totalSessions = allSessions.size();
            long healthySessions = totalSessions - expiredSessions.size();
            long expiredSessionsCount = expiredSessions.size();
            
            // Calculate average heartbeat age
            double averageHeartbeatAge = allSessions.stream()
                .mapToLong(session -> {
                    if (session.getLastHeartbeat() != null) {
                        return Instant.now().getEpochSecond() - session.getLastHeartbeat().getEpochSecond();
                    }
                    return 0;
                })
                .average()
                .orElse(0.0);
            
            return HeartbeatStatistics.builder()
                .totalSessions(totalSessions)
                .healthySessions(healthySessions)
                .expiredSessions(expiredSessionsCount)
                .averageHeartbeatAgeSeconds(averageHeartbeatAge)
                .lastCheckTime(Instant.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error getting heartbeat statistics", e);
            return HeartbeatStatistics.builder()
                .totalSessions(0)
                .healthySessions(0)
                .expiredSessions(0)
                .averageHeartbeatAgeSeconds(0.0)
                .lastCheckTime(Instant.now())
                .build();
        }
    }

    /**
     * Heartbeat statistics data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class HeartbeatStatistics {
        private long totalSessions;
        private long healthySessions;
        private long expiredSessions;
        private double averageHeartbeatAgeSeconds;
        private Instant lastCheckTime;
        
        public double getHealthyPercentage() {
            return totalSessions > 0 ? (double) healthySessions / totalSessions * 100.0 : 100.0;
        }
        
        public double getExpiredPercentage() {
            return totalSessions > 0 ? (double) expiredSessions / totalSessions * 100.0 : 0.0;
        }
    }
}
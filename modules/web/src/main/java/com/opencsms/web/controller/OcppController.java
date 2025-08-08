package com.opencsms.web.controller;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.ocpp.handler.OcppWebSocketHandler;
import com.opencsms.ocpp.session.OcppSession;
import com.opencsms.ocpp.session.OcppSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for OCPP monitoring and management.
 */
@RestController
@RequestMapping("/api/v1/ocpp")
@RequiredArgsConstructor
@Validated
@Slf4j
public class OcppController {

    private final OcppSessionManager sessionManager;
    private final OcppWebSocketHandler webSocketHandler;

    /**
     * Get all active OCPP sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<OcppSession>> getAllSessions() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting OCPP sessions for tenant: {}", tenantId);
        
        List<OcppSession> sessions = tenantId != null ? 
            sessionManager.getSessionsByTenant(tenantId) : 
            sessionManager.getAllSessions();
        
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get sessions by OCPP version.
     */
    @GetMapping("/sessions/version/{ocppVersion}")
    public ResponseEntity<List<OcppSession>> getSessionsByVersion(@PathVariable String ocppVersion) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting OCPP {} sessions for tenant: {}", ocppVersion, tenantId);
        
        List<OcppSession> sessions = sessionManager.getSessionsByVersion(ocppVersion);
        
        // Filter by tenant if needed
        if (tenantId != null) {
            sessions = sessions.stream()
                .filter(session -> tenantId.equals(session.getTenantId()))
                .toList();
        }
        
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get session for a specific station.
     */
    @GetMapping("/sessions/station/{stationId}")
    public ResponseEntity<OcppSession> getSessionByStation(@PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting OCPP session for station {} for tenant: {}", stationId, tenantId);
        
        Optional<OcppSession> session = sessionManager.getSessionByStationId(stationId, tenantId);
        return session.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if a station is connected via OCPP.
     */
    @GetMapping("/stations/{stationId}/connected")
    public ResponseEntity<ConnectionStatus> isStationConnected(@PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.debug("Checking connection status for station {} for tenant: {}", stationId, tenantId);
        
        boolean connected = sessionManager.isStationConnected(stationId, tenantId);
        ConnectionStatus status = new ConnectionStatus(stationId, connected);
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get session statistics.
     */
    @GetMapping("/sessions/statistics")
    public ResponseEntity<OcppSessionManager.SessionStatistics> getSessionStatistics() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting OCPP session statistics for tenant: {}", tenantId);
        
        OcppSessionManager.SessionStatistics statistics = sessionManager.getStatistics();
        
        // If tenant-specific request, we could filter statistics here
        // For now, return global statistics as admin-only feature
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get sessions with expired heartbeats.
     */
    @GetMapping("/sessions/expired-heartbeat")
    public ResponseEntity<List<OcppSession>> getSessionsWithExpiredHeartbeat(
            @RequestParam(defaultValue = "600") int timeoutSeconds) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting OCPP sessions with expired heartbeat for tenant: {} (timeout: {}s)", 
                tenantId, timeoutSeconds);
        
        List<OcppSession> sessions = sessionManager.getSessionsWithExpiredHeartbeat(timeoutSeconds);
        
        // Filter by tenant if needed
        if (tenantId != null) {
            sessions = sessions.stream()
                .filter(session -> tenantId.equals(session.getTenantId()))
                .toList();
        }
        
        return ResponseEntity.ok(sessions);
    }

    /**
     * Send a message to a specific station.
     */
    @PostMapping("/stations/{stationId}/send-message")
    public ResponseEntity<MessageSendResult> sendMessageToStation(
            @PathVariable @NotBlank String stationId, 
            @RequestBody @NotBlank String message) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Sending message to station {} for tenant: {}", stationId, tenantId);
        
        try {
            boolean sent = webSocketHandler.sendMessageToStation(stationId, tenantId, message);
            MessageSendResult result = new MessageSendResult(stationId, sent, 
                sent ? "Message sent successfully" : "Station not connected or message failed");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error sending message to station {}", stationId, e);
            MessageSendResult result = new MessageSendResult(stationId, false, 
                "Error: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Force disconnect a station's OCPP session.
     */
    @PostMapping("/stations/{stationId}/disconnect")
    public ResponseEntity<Void> disconnectStation(@PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Disconnecting station {} for tenant: {}", stationId, tenantId);
        
        try {
            Optional<OcppSession> session = sessionManager.getSessionByStationId(stationId, tenantId);
            if (session.isPresent()) {
                OcppSession ocppSession = session.get();
                if (ocppSession.isOpen()) {
                    ocppSession.getWebSocketSession().close();
                }
                sessionManager.removeSessionByStationId(stationId, tenantId);
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error disconnecting station {}", stationId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Update heartbeat for a session manually.
     */
    @PostMapping("/sessions/{sessionId}/heartbeat")
    public ResponseEntity<Void> updateHeartbeat(@PathVariable @NotBlank String sessionId) {
        log.debug("Manually updating heartbeat for session: {}", sessionId);
        
        try {
            sessionManager.updateHeartbeat(sessionId);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Error updating heartbeat for session {}", sessionId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Connection status response.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ConnectionStatus {
        private String stationId;
        private boolean connected;
    }

    /**
     * Message send result response.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MessageSendResult {
        private String stationId;
        private boolean sent;
        private String message;
    }
}
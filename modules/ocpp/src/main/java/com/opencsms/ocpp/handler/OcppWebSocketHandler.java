package com.opencsms.ocpp.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsms.core.tenant.TenantContext;
import com.opencsms.ocpp.message.OcppMessage;
import com.opencsms.ocpp.message.OcppMessageRouter;
import com.opencsms.ocpp.session.OcppSession;
import com.opencsms.ocpp.session.OcppSessionManager;
import com.opencsms.service.station.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.URI;

/**
 * WebSocket handler for OCPP messages from charging stations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcppWebSocketHandler implements WebSocketHandler {

    private final OcppSessionManager sessionManager;
    private final OcppMessageRouter messageRouter;
    private final StationService stationService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            // Extract connection attributes
            String stationId = (String) session.getAttributes().get("stationId");
            String tenantId = (String) session.getAttributes().get("tenantId");
            String ocppVersion = (String) session.getAttributes().get("ocppVersion");
            String clientIp = (String) session.getAttributes().get("clientIp");
            
            log.info("OCPP WebSocket connection established - Station: {}, Version: {}, Tenant: {}, IP: {}", 
                    stationId, ocppVersion, tenantId, clientIp);
            
            // Set tenant context
            if (tenantId != null) {
                TenantContext.setCurrentTenant(tenantId);
            }
            
            // Register session
            OcppSession ocppSession = sessionManager.registerSession(session, stationId, 
                                                                   tenantId, ocppVersion, clientIp);
            
            // Send welcome message (optional, depending on OCPP version)
            sendWelcomeMessage(session, ocppVersion);
            
            log.info("OCPP session registered: {}", ocppSession);
            
        } catch (Exception e) {
            log.error("Error establishing OCPP connection", e);
            session.close(CloseStatus.SERVER_ERROR.withReason("Connection setup failed"));
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (!(message instanceof TextMessage)) {
            log.warn("Received non-text message from session: {}", session.getId());
            return;
        }

        String payload = ((TextMessage) message).getPayload();
        log.debug("Received OCPP message from {}: {}", session.getId(), payload);

        try {
            // Get session
            OcppSession ocppSession = sessionManager.getSession(session.getId())
                .orElseThrow(() -> new IllegalStateException("Session not found: " + session.getId()));
            
            // Set tenant context
            if (ocppSession.getTenantId() != null) {
                TenantContext.setCurrentTenant(ocppSession.getTenantId());
            }
            
            // Record message received
            ocppSession.recordMessageReceived();
            
            // Parse and route message
            OcppMessage ocppMessage = parseOcppMessage(payload, ocppSession.getOcppVersion());
            
            // Route message and get response
            messageRouter.routeMessage(ocppSession, ocppMessage).thenAccept(response -> {
                if (response != null && ocppMessage.isCall()) {
                    try {
                        // Convert response to JSON and send back
                        String responseJson = createResponseJson(response);
                        sendMessage(session, responseJson);
                    } catch (Exception e) {
                        log.error("Error sending OCPP response", e);
                    }
                }
            }).exceptionally(throwable -> {
                log.error("Error processing OCPP message", throwable);
                sendErrorResponse(session, throwable.getMessage());
                return null;
            });
            
            // Handle heartbeat messages specially
            if ("Heartbeat".equals(ocppMessage.getAction())) {
                sessionManager.updateHeartbeat(session.getId());
                stationService.updateHeartbeat(ocppSession.getStationId());
            }
            
        } catch (Exception e) {
            log.error("Error handling OCPP message from session {}: {}", session.getId(), e.getMessage(), e);
            sendErrorResponse(session, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error for OCPP session {}: {}", session.getId(), exception.getMessage(), exception);
        
        // Get session info for logging
        sessionManager.getSession(session.getId()).ifPresent(ocppSession -> {
            log.error("Transport error for station: {} (tenant: {})", 
                     ocppSession.getStationId(), ocppSession.getTenantId());
            
            // Mark station as disconnected
            try {
                if (ocppSession.getTenantId() != null) {
                    TenantContext.setCurrentTenant(ocppSession.getTenantId());
                }
                stationService.markDisconnected(ocppSession.getStationId());
            } catch (Exception e) {
                log.error("Error marking station as disconnected", e);
            } finally {
                TenantContext.clear();
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("OCPP WebSocket connection closed - Session: {}, Status: {}", 
                session.getId(), closeStatus);
        
        // Get session info before removal
        sessionManager.getSession(session.getId()).ifPresent(ocppSession -> {
            log.info("OCPP connection closed for station: {} (tenant: {})", 
                    ocppSession.getStationId(), ocppSession.getTenantId());
            
            // Mark station as disconnected
            try {
                if (ocppSession.getTenantId() != null) {
                    TenantContext.setCurrentTenant(ocppSession.getTenantId());
                }
                stationService.markDisconnected(ocppSession.getStationId());
            } catch (Exception e) {
                log.error("Error marking station as disconnected", e);
            } finally {
                TenantContext.clear();
            }
        });
        
        // Remove session
        sessionManager.removeSession(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Send a message to a WebSocket session.
     */
    public void sendMessage(WebSocketSession session, String message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(message));
            
            // Update session statistics
            sessionManager.getSession(session.getId()).ifPresent(OcppSession::recordMessageSent);
            
            log.debug("Sent OCPP message to {}: {}", session.getId(), message);
        } else {
            log.warn("Attempted to send message to closed session: {}", session.getId());
        }
    }

    /**
     * Send message to a specific station.
     */
    public boolean sendMessageToStation(String stationId, String tenantId, String message) {
        return sessionManager.getSessionByStationId(stationId, tenantId)
            .map(ocppSession -> {
                try {
                    sendMessage(ocppSession.getWebSocketSession(), message);
                    return true;
                } catch (IOException e) {
                    log.error("Error sending message to station {}: {}", stationId, e.getMessage());
                    return false;
                }
            })
            .orElse(false);
    }

    private void sendWelcomeMessage(WebSocketSession session, String ocppVersion) {
        // OCPP doesn't have a standard welcome message, but we can log the connection
        log.info("OCPP {} connection ready for session: {}", ocppVersion, session.getId());
    }

    private OcppMessage parseOcppMessage(String payload, String ocppVersion) throws Exception {
        try {
            // Parse JSON array format: [MessageType, MessageId, Action, Payload]
            Object[] messageArray = objectMapper.readValue(payload, Object[].class);
            
            if (messageArray.length < 3) {
                throw new IllegalArgumentException("Invalid OCPP message format");
            }
            
            Integer messageType = (Integer) messageArray[0];
            String messageId = (String) messageArray[1];
            String action = null;
            Object messagePayload = null;
            
            if (messageType == 2) { // CALL
                if (messageArray.length != 4) {
                    throw new IllegalArgumentException("Invalid CALL message format");
                }
                action = (String) messageArray[2];
                messagePayload = messageArray[3];
            } else if (messageType == 3) { // CALL_RESULT
                if (messageArray.length != 3) {
                    throw new IllegalArgumentException("Invalid CALL_RESULT message format");
                }
                messagePayload = messageArray[2];
            } else if (messageType == 4) { // CALL_ERROR
                if (messageArray.length != 5) {
                    throw new IllegalArgumentException("Invalid CALL_ERROR message format");
                }
                action = (String) messageArray[2]; // Error code
                messagePayload = messageArray[4]; // Error details
            } else {
                throw new IllegalArgumentException("Unknown message type: " + messageType);
            }
            
            return OcppMessage.builder()
                .messageType(messageType)
                .messageId(messageId)
                .action(action)
                .payload(messagePayload)
                .ocppVersion(ocppVersion)
                .rawMessage(payload)
                .build();
                
        } catch (Exception e) {
            log.error("Error parsing OCPP message: {}", payload, e);
            throw new Exception("Invalid OCPP message format: " + e.getMessage(), e);
        }
    }

    private void sendErrorResponse(WebSocketSession session, String errorMessage) {
        try {
            // Send a generic error response
            String errorResponse = objectMapper.writeValueAsString(new Object[]{
                4, // CALL_ERROR
                "error", // Message ID
                "GenericError", // Error code
                errorMessage, // Error description
                "{}" // Error details
            });
            
            sendMessage(session, errorResponse);
        } catch (Exception e) {
            log.error("Error sending error response", e);
        }
    }

    private String createResponseJson(OcppMessage response) throws Exception {
        Object[] responseArray;
        
        if (response.isCallResult()) {
            responseArray = new Object[]{
                OcppMessage.CALL_RESULT,
                response.getMessageId(),
                response.getPayload()
            };
        } else if (response.isCallError()) {
            responseArray = new Object[]{
                OcppMessage.CALL_ERROR,
                response.getMessageId(),
                response.getAction(), // Error code
                response.getPayload(), // Error description
                "{}" // Error details
            };
        } else {
            throw new IllegalArgumentException("Invalid response message type: " + response.getMessageType());
        }
        
        return objectMapper.writeValueAsString(responseArray);
    }
}
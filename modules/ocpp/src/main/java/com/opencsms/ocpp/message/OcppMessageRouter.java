package com.opencsms.ocpp.message;

import com.opencsms.ocpp.message.common.OcppErrorCode;
import com.opencsms.ocpp.session.OcppSession;
import com.opencsms.service.ocpp.model.ParsedOcppMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Routes OCPP messages to appropriate handlers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcppMessageRouter {

    private final OcppMessageParser messageParser;
    private final OcppMessageValidator messageValidator;
    private final com.opencsms.ocpp.service.BootNotificationService bootNotificationService;
    private final com.opencsms.ocpp.service.HeartbeatService heartbeatService;
    private final com.opencsms.ocpp.service.StatusNotificationService statusNotificationService;
    private final com.opencsms.service.ocpp.StartTransactionService startTransactionService;
    private final com.opencsms.service.ocpp.StopTransactionService stopTransactionService;
    private final com.opencsms.service.ocpp.AuthorizeService authorizeService;

    /**
     * Route an incoming OCPP message to the appropriate handler.
     */
    public CompletableFuture<OcppMessage> routeMessage(OcppSession session, OcppMessage message) {
        log.debug("Routing message: {} for station: {}", message, session.getStationId());
        
        try {
            // First parse the message using the new parser
            ParsedOcppMessage parsedMessage = messageParser.parseMessage(message.getRawMessage(), session.getOcppVersion());
            
            // Validate the parsed message
            var validationResult = messageValidator.validate(parsedMessage);
            if (validationResult.isInvalid()) {
                log.warn("Invalid OCPP message from station {}: {}", 
                        session.getStationId(), validationResult.getErrorDescription());
                
                String errorResponse = messageParser.createErrorMessage(
                    parsedMessage.getMessageId(),
                    validationResult.getErrorCode(),
                    validationResult.getErrorDescription(),
                    null
                );
                
                return CompletableFuture.completedFuture(
                    OcppMessage.builder()
                        .messageType(OcppMessage.CALL_ERROR)
                        .messageId(parsedMessage.getMessageId())
                        .action(validationResult.getErrorCode().getCode())
                        .payload(validationResult.getErrorDescription())
                        .rawMessage(errorResponse)
                        .build()
                );
            }
            
            if (parsedMessage.isCall()) {
                return handleCall(session, parsedMessage);
            } else if (parsedMessage.isCallResult()) {
                return handleCallResult(session, parsedMessage);
            } else if (parsedMessage.isCallError()) {
                return handleCallError(session, parsedMessage);
            } else {
                log.warn("Unknown message type: {} from station: {}", 
                        parsedMessage.getMessageType(), session.getStationId());
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception e) {
            log.error("Error routing message: {} from station: {}", 
                     message, session.getStationId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<OcppMessage> handleCall(OcppSession session, ParsedOcppMessage message) {
        String action = message.getAction();
        log.debug("Handling CALL action: {} from station: {}", action, session.getStationId());
        
        try {
            Object responsePayload;
            
            switch (action) {
                case "BootNotification":
                    responsePayload = bootNotificationService.handleBootNotification(session, message);
                    break;
                case "Heartbeat":
                    responsePayload = heartbeatService.handleHeartbeat(session, message);
                    break;
                case "StatusNotification":
                    responsePayload = statusNotificationService.handleStatusNotification(session, message);
                    break;
                case "MeterValues":
                    responsePayload = handleMeterValues(session, message);
                    break;
                case "StartTransaction":
                    responsePayload = handleStartTransaction(session, message);
                    break;
                case "StopTransaction":
                    responsePayload = handleStopTransaction(session, message);
                    break;
                case "Authorize":
                    responsePayload = handleAuthorize(session, message);
                    break;
                case "DataTransfer":
                    responsePayload = handleDataTransfer(session, message);
                    break;
                case "DiagnosticsStatusNotification":
                    responsePayload = handleDiagnosticsStatusNotification(session, message);
                    break;
                case "FirmwareStatusNotification":
                    responsePayload = handleFirmwareStatusNotification(session, message);
                    break;
                default:
                    log.warn("Unhandled CALL action: {} from station: {}", action, session.getStationId());
                    return createErrorResponse(message.getMessageId(), "NotSupported", 
                                             "Action not supported: " + action);
            }
            
            // Create response message
            String responseJson = messageParser.createResponseMessage(message, responsePayload);
            
            return CompletableFuture.completedFuture(
                OcppMessage.builder()
                    .messageType(OcppMessage.CALL_RESULT)
                    .messageId(message.getMessageId())
                    .payload(responsePayload)
                    .rawMessage(responseJson)
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Error handling CALL message: {} from station: {}", 
                     message.getAction(), session.getStationId(), e);
            return createErrorResponse(message.getMessageId(), "InternalError", 
                                     "Internal processing error: " + e.getMessage());
        }
    }

    private CompletableFuture<OcppMessage> handleCallResult(OcppSession session, ParsedOcppMessage message) {
        log.debug("Handling CALL_RESULT for message ID: {} from station: {}", 
                 message.getMessageId(), session.getStationId());
        
        // Remove pending message
        OcppSession.PendingMessage pendingMessage = session.removePendingMessage(message.getMessageId());
        if (pendingMessage != null) {
            log.debug("Received response for pending message: {} from station: {}", 
                     pendingMessage.getAction(), session.getStationId());
        } else {
            log.warn("Received CALL_RESULT for unknown message ID: {} from station: {}", 
                    message.getMessageId(), session.getStationId());
        }
        
        return CompletableFuture.completedFuture(
            OcppMessage.builder()
                .messageType(OcppMessage.CALL_RESULT)
                .messageId(message.getMessageId())
                .payload(message.getPayload())
                .rawMessage(message.getRawMessage())
                .build()
        );
    }

    private CompletableFuture<OcppMessage> handleCallError(OcppSession session, ParsedOcppMessage message) {
        log.warn("Handling CALL_ERROR for message ID: {} from station: {} - Error: {}", 
                message.getMessageId(), session.getStationId(), message.getAction());
        
        // Remove pending message
        OcppSession.PendingMessage pendingMessage = session.removePendingMessage(message.getMessageId());
        if (pendingMessage != null) {
            log.error("Received error response for pending message: {} from station: {} - Error: {}", 
                     pendingMessage.getAction(), session.getStationId(), message.getPayload());
        }
        
        return CompletableFuture.completedFuture(
            OcppMessage.builder()
                .messageType(OcppMessage.CALL_ERROR)
                .messageId(message.getMessageId())
                .action(message.getAction())
                .payload(message.getPayload())
                .rawMessage(message.getRawMessage())
                .build()
        );
    }

    // TODO: Placeholder methods for unimplemented OCPP message handlers
    // These should be implemented as proper services similar to BootNotificationService,
    // HeartbeatService, and StatusNotificationService
    
    private Object handleMeterValues(OcppSession session, ParsedOcppMessage message) {
        log.debug("Received MeterValues from station: {} - Not implemented yet", session.getStationId());
        // TODO: Implement MeterValuesService
        return new EmptyResponse();
    }
    
    private Object handleStartTransaction(OcppSession session, ParsedOcppMessage message) {
        log.info("Received StartTransaction from station: {}", session.getStationId());
        
        // Create ParsedOcppMessage with session context
        ParsedOcppMessage contextMessage = ParsedOcppMessage.builder()
            .messageType(message.getMessageType())
            .messageId(message.getMessageId())
            .action(message.getAction())
            .payload(message.getPayload())
            .rawMessage(message.getRawMessage())
            .ocppVersion(message.getOcppVersion())
            .stationSerial(session.getStationId())
            .tenantId(session.getTenantId())
            .build();
        
        if ("1.6".equals(message.getOcppVersion())) {
            return startTransactionService.processStartTransaction16(contextMessage);
        } else if ("2.0.1".equals(message.getOcppVersion())) {
            return startTransactionService.processTransactionEventStarted(contextMessage);
        } else {
            log.warn("Unsupported OCPP version for StartTransaction: {}", message.getOcppVersion());
            return createSimpleErrorResponse("NotSupported", "Unsupported OCPP version");
        }
    }
    
    private Object handleStopTransaction(OcppSession session, ParsedOcppMessage message) {
        log.info("Received StopTransaction from station: {}", session.getStationId());
        
        // Create ParsedOcppMessage with session context
        ParsedOcppMessage contextMessage = ParsedOcppMessage.builder()
            .messageType(message.getMessageType())
            .messageId(message.getMessageId())
            .action(message.getAction())
            .payload(message.getPayload())
            .rawMessage(message.getRawMessage())
            .ocppVersion(message.getOcppVersion())
            .stationSerial(session.getStationId())
            .tenantId(session.getTenantId())
            .build();
        
        if ("1.6".equals(message.getOcppVersion())) {
            return stopTransactionService.processStopTransaction16(contextMessage);
        } else if ("2.0.1".equals(message.getOcppVersion())) {
            return stopTransactionService.processTransactionEventEnded(contextMessage);
        } else {
            log.warn("Unsupported OCPP version for StopTransaction: {}", message.getOcppVersion());
            return createSimpleErrorResponse("NotSupported", "Unsupported OCPP version");
        }
    }
    
    private Object handleAuthorize(OcppSession session, ParsedOcppMessage message) {
        log.debug("Received Authorize from station: {}", session.getStationId());
        
        // Create ParsedOcppMessage with session context
        ParsedOcppMessage contextMessage = ParsedOcppMessage.builder()
            .messageType(message.getMessageType())
            .messageId(message.getMessageId())
            .action(message.getAction())
            .payload(message.getPayload())
            .rawMessage(message.getRawMessage())
            .ocppVersion(message.getOcppVersion())
            .stationSerial(session.getStationId())
            .tenantId(session.getTenantId())
            .build();
        
        if ("1.6".equals(message.getOcppVersion())) {
            return authorizeService.processAuthorize16(contextMessage);
        } else if ("2.0.1".equals(message.getOcppVersion())) {
            return authorizeService.processAuthorize201(contextMessage);
        } else {
            log.warn("Unsupported OCPP version for Authorize: {}", message.getOcppVersion());
            return createSimpleErrorResponse("NotSupported", "Unsupported OCPP version");
        }
    }
    
    private Object handleDataTransfer(OcppSession session, ParsedOcppMessage message) {
        log.debug("Received DataTransfer from station: {} - Not implemented yet", session.getStationId());
        // TODO: Implement DataTransferService
        return new EmptyResponse();
    }
    
    private Object handleDiagnosticsStatusNotification(OcppSession session, ParsedOcppMessage message) {
        log.debug("Received DiagnosticsStatusNotification from station: {} - Not implemented yet", session.getStationId());
        // TODO: Implement DiagnosticsService
        return new EmptyResponse();
    }
    
    private Object handleFirmwareStatusNotification(OcppSession session, ParsedOcppMessage message) {
        log.debug("Received FirmwareStatusNotification from station: {} - Not implemented yet", session.getStationId());
        // TODO: Implement FirmwareService
        return new EmptyResponse();
    }

    // Helper Methods

    private CompletableFuture<OcppMessage> createCallResult(String messageId, Object payload) {
        OcppMessage response = OcppMessage.builder()
            .messageType(OcppMessage.CALL_RESULT)
            .messageId(messageId)
            .payload(payload)
            .build();
        
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<OcppMessage> createErrorResponse(String messageId, String errorCode, String errorDescription) {
        OcppMessage response = OcppMessage.builder()
            .messageType(OcppMessage.CALL_ERROR)
            .messageId(messageId)
            .action(errorCode)
            .payload(errorDescription)
            .build();
        
        return CompletableFuture.completedFuture(response);
    }

    private Map<String, Object> createSimpleErrorResponse(String errorCode, String description) {
        return Map.of(
            "status", "Rejected",
            "errorCode", errorCode,
            "errorDescription", description
        );
    }

    /**
     * Empty response class for operations that don't return specific data.
     * Used as placeholder until proper response classes are implemented.
     */
    public static class EmptyResponse {
        // Empty response for operations that don't return data
        // Will be replaced by proper response classes when services are implemented
    }
}
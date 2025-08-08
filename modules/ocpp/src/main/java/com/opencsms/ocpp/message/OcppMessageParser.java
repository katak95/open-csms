package com.opencsms.ocpp.message;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsms.ocpp.message.common.OcppErrorCode;
import com.opencsms.ocpp.message.common.OcppMessageType;
import com.opencsms.ocpp.message.v16.Ocpp16MessageType;
import com.opencsms.ocpp.message.v201.Ocpp201MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for OCPP messages supporting versions 1.6 and 2.0.1.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcppMessageParser {

    private final ObjectMapper objectMapper;
    
    // Cache for parsed message types
    private static final Map<String, Class<?>> messageClassCache = new HashMap<>();
    
    static {
        initializeMessageClassCache();
    }

    /**
     * Parse raw OCPP message into structured format.
     */
    public ParsedOcppMessage parseMessage(String rawMessage, String ocppVersion) throws OcppMessageParseException {
        try {
            // Parse as JSON array
            List<Object> messageArray = objectMapper.readValue(rawMessage, new TypeReference<List<Object>>() {});
            
            if (messageArray.size() < 3) {
                throw new OcppMessageParseException("Invalid OCPP message format - insufficient elements");
            }
            
            // Extract message type
            Integer messageTypeValue = (Integer) messageArray.get(0);
            OcppMessageType messageType = OcppMessageType.fromValue(messageTypeValue);
            
            // Extract message ID
            String messageId = (String) messageArray.get(1);
            
            ParsedOcppMessage.ParsedOcppMessageBuilder builder = ParsedOcppMessage.builder()
                .messageType(messageType)
                .messageId(messageId)
                .ocppVersion(ocppVersion)
                .rawMessage(rawMessage);
            
            switch (messageType) {
                case CALL:
                    return parseCallMessage(messageArray, builder, ocppVersion);
                case CALL_RESULT:
                    return parseCallResultMessage(messageArray, builder, ocppVersion);
                case CALL_ERROR:
                    return parseCallErrorMessage(messageArray, builder);
                default:
                    throw new OcppMessageParseException("Unsupported message type: " + messageType);
            }
            
        } catch (Exception e) {
            log.error("Error parsing OCPP message: {}", rawMessage, e);
            throw new OcppMessageParseException("Failed to parse OCPP message: " + e.getMessage(), e);
        }
    }

    /**
     * Create response message from parsed request.
     */
    public String createResponseMessage(ParsedOcppMessage request, Object responsePayload) throws OcppMessageParseException {
        try {
            Object[] responseArray = {
                OcppMessageType.CALL_RESULT.getValue(),
                request.getMessageId(),
                responsePayload
            };
            
            return objectMapper.writeValueAsString(responseArray);
            
        } catch (Exception e) {
            throw new OcppMessageParseException("Failed to create response message", e);
        }
    }

    /**
     * Create error response message.
     */
    public String createErrorMessage(String messageId, OcppErrorCode errorCode, String errorDescription, Object errorDetails) throws OcppMessageParseException {
        try {
            Object[] errorArray = {
                OcppMessageType.CALL_ERROR.getValue(),
                messageId,
                errorCode.getCode(),
                errorDescription,
                errorDetails != null ? errorDetails : new HashMap<>()
            };
            
            return objectMapper.writeValueAsString(errorArray);
            
        } catch (Exception e) {
            throw new OcppMessageParseException("Failed to create error message", e);
        }
    }

    private ParsedOcppMessage parseCallMessage(List<Object> messageArray, ParsedOcppMessage.ParsedOcppMessageBuilder builder, String ocppVersion) throws Exception {
        if (messageArray.size() != 4) {
            throw new OcppMessageParseException("Invalid CALL message format - expected 4 elements");
        }
        
        String action = (String) messageArray.get(2);
        Object payloadObj = messageArray.get(3);
        
        builder.action(action);
        
        // Parse payload based on OCPP version and action
        Object parsedPayload = parsePayload(action, payloadObj, ocppVersion, true);
        builder.payload(parsedPayload);
        
        return builder.build();
    }

    private ParsedOcppMessage parseCallResultMessage(List<Object> messageArray, ParsedOcppMessage.ParsedOcppMessageBuilder builder, String ocppVersion) throws Exception {
        if (messageArray.size() != 3) {
            throw new OcppMessageParseException("Invalid CALL_RESULT message format - expected 3 elements");
        }
        
        Object payloadObj = messageArray.get(2);
        
        // For CALL_RESULT, we need context about the original request to parse correctly
        // For now, store as JsonNode for flexible handling
        JsonNode payloadNode = objectMapper.valueToTree(payloadObj);
        builder.payload(payloadNode);
        
        return builder.build();
    }

    private ParsedOcppMessage parseCallErrorMessage(List<Object> messageArray, ParsedOcppMessage.ParsedOcppMessageBuilder builder) throws Exception {
        if (messageArray.size() != 5) {
            throw new OcppMessageParseException("Invalid CALL_ERROR message format - expected 5 elements");
        }
        
        String errorCode = (String) messageArray.get(2);
        String errorDescription = (String) messageArray.get(3);
        Object errorDetails = messageArray.get(4);
        
        builder.action(errorCode);
        
        OcppErrorResponse errorResponse = new OcppErrorResponse(errorCode, errorDescription, errorDetails);
        builder.payload(errorResponse);
        
        return builder.build();
    }

    private Object parsePayload(String action, Object payloadObj, String ocppVersion, boolean isRequest) throws Exception {
        String classKey = createClassKey(action, ocppVersion, isRequest);
        Class<?> payloadClass = messageClassCache.get(classKey);
        
        if (payloadClass != null) {
            return objectMapper.convertValue(payloadObj, payloadClass);
        } else {
            log.debug("No specific payload class found for action: {} (version: {}, request: {})", action, ocppVersion, isRequest);
            // Return as JsonNode for flexible handling
            return objectMapper.valueToTree(payloadObj);
        }
    }

    private String createClassKey(String action, String ocppVersion, boolean isRequest) {
        return String.format("%s:%s:%s", ocppVersion, action, isRequest ? "request" : "response");
    }

    private static void initializeMessageClassCache() {
        // OCPP 1.6 Request classes
        messageClassCache.put("1.6:BootNotification:request", 
            com.opencsms.ocpp.message.v16.request.BootNotificationRequest.class);
        messageClassCache.put("1.6:Heartbeat:request", 
            com.opencsms.ocpp.message.v16.request.HeartbeatRequest.class);
        messageClassCache.put("1.6:StatusNotification:request", 
            com.opencsms.ocpp.message.v16.request.StatusNotificationRequest.class);
        messageClassCache.put("1.6:StartTransaction:request", 
            com.opencsms.ocpp.message.v16.request.StartTransactionRequest.class);
        
        // OCPP 1.6 Response classes
        messageClassCache.put("1.6:BootNotification:response", 
            com.opencsms.ocpp.message.v16.response.BootNotificationResponse.class);
        messageClassCache.put("1.6:Heartbeat:response", 
            com.opencsms.ocpp.message.v16.response.HeartbeatResponse.class);
        messageClassCache.put("1.6:StatusNotification:response", 
            com.opencsms.ocpp.message.v16.response.StatusNotificationResponse.class);
        messageClassCache.put("1.6:StartTransaction:response", 
            com.opencsms.ocpp.message.v16.response.StartTransactionResponse.class);
        
        // OCPP 2.0.1 Request classes
        messageClassCache.put("2.0.1:BootNotification:request", 
            com.opencsms.ocpp.message.v201.request.BootNotificationRequest.class);
        
        // OCPP 2.0.1 Response classes
        messageClassCache.put("2.0.1:BootNotification:response", 
            com.opencsms.ocpp.message.v201.response.BootNotificationResponse.class);
        
        log.info("Initialized OCPP message class cache with {} entries", messageClassCache.size());
    }

    /**
     * Exception for OCPP message parsing errors.
     */
    public static class OcppMessageParseException extends Exception {
        public OcppMessageParseException(String message) {
            super(message);
        }
        
        public OcppMessageParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * OCPP error response structure.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class OcppErrorResponse {
        private String errorCode;
        private String errorDescription;
        private Object errorDetails;
    }
}
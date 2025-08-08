package com.opencsms.ocpp.message;

import com.opencsms.ocpp.message.common.OcppMessageType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Parsed OCPP message with structured payload.
 */
@Data
@Builder
public class ParsedOcppMessage {
    
    private OcppMessageType messageType;
    private String messageId;
    private String action;
    private Object payload;
    private String ocppVersion;
    private String rawMessage;
    
    @Builder.Default
    private Instant parsedAt = Instant.now();
    
    /**
     * Check if this is a CALL message.
     */
    public boolean isCall() {
        return messageType == OcppMessageType.CALL;
    }
    
    /**
     * Check if this is a CALL_RESULT message.
     */
    public boolean isCallResult() {
        return messageType == OcppMessageType.CALL_RESULT;
    }
    
    /**
     * Check if this is a CALL_ERROR message.
     */
    public boolean isCallError() {
        return messageType == OcppMessageType.CALL_ERROR;
    }
    
    /**
     * Get the payload as a specific type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadAs(Class<T> type) {
        if (payload == null) {
            return null;
        }
        
        if (type.isAssignableFrom(payload.getClass())) {
            return (T) payload;
        }
        
        throw new ClassCastException("Cannot cast payload to " + type.getName() + 
                                   ", actual type: " + payload.getClass().getName());
    }
    
    /**
     * Check if the action matches the given action string.
     */
    public boolean isAction(String actionName) {
        return actionName != null && actionName.equals(action);
    }
    
    /**
     * Check if the message is for OCPP version 1.6.
     */
    public boolean isOcpp16() {
        return "1.6".equals(ocppVersion);
    }
    
    /**
     * Check if the message is for OCPP version 2.0.1.
     */
    public boolean isOcpp201() {
        return "2.0.1".equals(ocppVersion);
    }
    
    @Override
    public String toString() {
        return String.format("ParsedOcppMessage{type=%s, id='%s', action='%s', version='%s'}", 
                           messageType, messageId, action, ocppVersion);
    }
}
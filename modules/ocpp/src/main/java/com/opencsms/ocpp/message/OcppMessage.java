package com.opencsms.ocpp.message;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents an OCPP message.
 */
@Data
@Builder
public class OcppMessage {
    
    public static final int CALL = 2;
    public static final int CALL_RESULT = 3;
    public static final int CALL_ERROR = 4;
    
    private Integer messageType;
    private String messageId;
    private String action;
    private Object payload;
    private String ocppVersion;
    private String rawMessage;
    
    @Builder.Default
    private Instant receivedAt = Instant.now();
    
    /**
     * Check if this is a CALL message.
     */
    public boolean isCall() {
        return CALL == messageType;
    }
    
    /**
     * Check if this is a CALL_RESULT message.
     */
    public boolean isCallResult() {
        return CALL_RESULT == messageType;
    }
    
    /**
     * Check if this is a CALL_ERROR message.
     */
    public boolean isCallError() {
        return CALL_ERROR == messageType;
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
        
        throw new ClassCastException("Cannot cast payload to " + type.getName());
    }
    
    /**
     * Get message type name.
     */
    public String getMessageTypeName() {
        switch (messageType) {
            case CALL: return "CALL";
            case CALL_RESULT: return "CALL_RESULT";
            case CALL_ERROR: return "CALL_ERROR";
            default: return "UNKNOWN";
        }
    }
    
    @Override
    public String toString() {
        return String.format("OcppMessage{type=%s(%d), id='%s', action='%s', version='%s'}", 
                           getMessageTypeName(), messageType, messageId, action, ocppVersion);
    }
}
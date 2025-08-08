package com.opencsms.ocpp.message.common;

/**
 * OCPP message types according to the specification.
 */
public enum OcppMessageType {
    
    CALL(2),
    CALL_RESULT(3),
    CALL_ERROR(4);
    
    private final int value;
    
    OcppMessageType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static OcppMessageType fromValue(int value) {
        for (OcppMessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OCPP message type: " + value);
    }
}
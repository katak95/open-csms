package com.opencsms.ocpp.message.common;

/**
 * OCPP error codes according to the specification.
 */
public enum OcppErrorCode {
    
    // Common errors
    NOT_IMPLEMENTED("NotImplemented"),
    NOT_SUPPORTED("NotSupported"),
    INTERNAL_ERROR("InternalError"),
    PROTOCOL_ERROR("ProtocolError"),
    SECURITY_ERROR("SecurityError"),
    FORMATION_VIOLATION("FormationViolation"),
    PROPERTY_CONSTRAINT_VIOLATION("PropertyConstraintViolation"),
    OCCURRENCE_CONSTRAINT_VIOLATION("OccurrenceConstraintViolation"),
    TYPE_CONSTRAINT_VIOLATION("TypeConstraintViolation"),
    GENERIC_ERROR("GenericError"),
    
    // OCPP 1.6 specific errors
    MESSAGE_TYPE_NOT_SUPPORTED("MessageTypeNotSupported"),
    REQUEST_NOT_SUPPORTED("RequestNotSupported"),
    
    // OCPP 2.0.1 specific errors
    RPCFRAMEWORK_ERROR("RpcFrameworkError"),
    FORMAT_VIOLATION("FormatViolation");
    
    private final String code;
    
    OcppErrorCode(String code) {
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
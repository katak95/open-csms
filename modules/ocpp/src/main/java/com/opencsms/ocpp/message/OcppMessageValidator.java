package com.opencsms.ocpp.message;

import com.opencsms.ocpp.message.common.OcppErrorCode;
import com.opencsms.ocpp.message.v16.request.BootNotificationRequest;
import com.opencsms.ocpp.message.v16.request.StartTransactionRequest;
import com.opencsms.ocpp.message.v16.request.StatusNotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Validator for OCPP messages.
 */
@Service
@Slf4j
public class OcppMessageValidator {

    /**
     * Validate a parsed OCPP message.
     */
    public ValidationResult validate(ParsedOcppMessage message) {
        try {
            if (message.isCall()) {
                return validateCallMessage(message);
            } else if (message.isCallResult()) {
                return validateCallResultMessage(message);
            } else if (message.isCallError()) {
                return validateCallErrorMessage(message);
            } else {
                return ValidationResult.invalid(OcppErrorCode.PROTOCOL_ERROR, 
                    "Unknown message type: " + message.getMessageType());
            }
        } catch (Exception e) {
            log.error("Error validating OCPP message: {}", message, e);
            return ValidationResult.invalid(OcppErrorCode.INTERNAL_ERROR, 
                "Validation error: " + e.getMessage());
        }
    }

    private ValidationResult validateCallMessage(ParsedOcppMessage message) {
        if (!StringUtils.hasText(message.getAction())) {
            return ValidationResult.invalid(OcppErrorCode.PROTOCOL_ERROR, "Missing action");
        }
        
        if (!StringUtils.hasText(message.getMessageId())) {
            return ValidationResult.invalid(OcppErrorCode.PROTOCOL_ERROR, "Missing message ID");
        }
        
        // Validate based on OCPP version and action
        if (message.isOcpp16()) {
            return validateOcpp16CallMessage(message);
        } else if (message.isOcpp201()) {
            return validateOcpp201CallMessage(message);
        } else {
            return ValidationResult.invalid(OcppErrorCode.NOT_SUPPORTED, 
                "Unsupported OCPP version: " + message.getOcppVersion());
        }
    }

    private ValidationResult validateOcpp16CallMessage(ParsedOcppMessage message) {
        switch (message.getAction()) {
            case "BootNotification":
                return validateBootNotificationRequest16(message);
            case "Heartbeat":
                return ValidationResult.valid(); // No validation needed for heartbeat
            case "StatusNotification":
                return validateStatusNotificationRequest16(message);
            case "StartTransaction":
                return validateStartTransactionRequest16(message);
            case "Authorize":
            case "MeterValues":
            case "StopTransaction":
            case "DataTransfer":
            case "DiagnosticsStatusNotification":
            case "FirmwareStatusNotification":
                return ValidationResult.valid(); // Basic validation passed
            default:
                return ValidationResult.invalid(OcppErrorCode.NOT_SUPPORTED, 
                    "Unsupported OCPP 1.6 action: " + message.getAction());
        }
    }

    private ValidationResult validateOcpp201CallMessage(ParsedOcppMessage message) {
        switch (message.getAction()) {
            case "BootNotification":
                return validateBootNotificationRequest201(message);
            case "Heartbeat":
                return ValidationResult.valid();
            case "StatusNotification":
            case "TransactionEvent":
            case "Authorize":
            case "MeterValues":
            case "DataTransfer":
                return ValidationResult.valid(); // Basic validation passed
            default:
                return ValidationResult.invalid(OcppErrorCode.NOT_SUPPORTED, 
                    "Unsupported OCPP 2.0.1 action: " + message.getAction());
        }
    }

    private ValidationResult validateBootNotificationRequest16(ParsedOcppMessage message) {
        try {
            BootNotificationRequest request = message.getPayloadAs(BootNotificationRequest.class);
            
            if (!StringUtils.hasText(request.getChargePointModel())) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargePointModel is required");
            }
            
            if (!StringUtils.hasText(request.getChargePointVendor())) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargePointVendor is required");
            }
            
            // Validate length constraints
            if (request.getChargePointModel().length() > 50) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargePointModel exceeds maximum length of 50");
            }
            
            if (request.getChargePointVendor().length() > 50) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargePointVendor exceeds maximum length of 50");
            }
            
            return ValidationResult.valid();
            
        } catch (ClassCastException e) {
            return ValidationResult.invalid(OcppErrorCode.TYPE_CONSTRAINT_VIOLATION, 
                "Invalid BootNotification payload format");
        }
    }

    private ValidationResult validateBootNotificationRequest201(ParsedOcppMessage message) {
        try {
            com.opencsms.ocpp.message.v201.request.BootNotificationRequest request = 
                message.getPayloadAs(com.opencsms.ocpp.message.v201.request.BootNotificationRequest.class);
            
            if (request.getChargingStation() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargingStation is required");
            }
            
            if (!StringUtils.hasText(request.getChargingStation().getModel())) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargingStation.model is required");
            }
            
            if (!StringUtils.hasText(request.getChargingStation().getVendorName())) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "chargingStation.vendorName is required");
            }
            
            if (request.getReason() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "reason is required");
            }
            
            return ValidationResult.valid();
            
        } catch (ClassCastException e) {
            return ValidationResult.invalid(OcppErrorCode.TYPE_CONSTRAINT_VIOLATION, 
                "Invalid BootNotification payload format");
        }
    }

    private ValidationResult validateStatusNotificationRequest16(ParsedOcppMessage message) {
        try {
            StatusNotificationRequest request = message.getPayloadAs(StatusNotificationRequest.class);
            
            if (request.getConnectorId() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "connectorId is required");
            }
            
            if (request.getConnectorId() < 0) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "connectorId must be >= 0");
            }
            
            if (request.getErrorCode() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "errorCode is required");
            }
            
            if (request.getStatus() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "status is required");
            }
            
            return ValidationResult.valid();
            
        } catch (ClassCastException e) {
            return ValidationResult.invalid(OcppErrorCode.TYPE_CONSTRAINT_VIOLATION, 
                "Invalid StatusNotification payload format");
        }
    }

    private ValidationResult validateStartTransactionRequest16(ParsedOcppMessage message) {
        try {
            StartTransactionRequest request = message.getPayloadAs(StartTransactionRequest.class);
            
            if (request.getConnectorId() == null || request.getConnectorId() <= 0) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "connectorId is required and must be > 0");
            }
            
            if (!StringUtils.hasText(request.getIdTag())) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "idTag is required");
            }
            
            if (request.getIdTag().length() > 20) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "idTag exceeds maximum length of 20");
            }
            
            if (request.getMeterStart() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "meterStart is required");
            }
            
            if (request.getTimestamp() == null) {
                return ValidationResult.invalid(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, 
                    "timestamp is required");
            }
            
            return ValidationResult.valid();
            
        } catch (ClassCastException e) {
            return ValidationResult.invalid(OcppErrorCode.TYPE_CONSTRAINT_VIOLATION, 
                "Invalid StartTransaction payload format");
        }
    }

    private ValidationResult validateCallResultMessage(ParsedOcppMessage message) {
        if (!StringUtils.hasText(message.getMessageId())) {
            return ValidationResult.invalid(OcppErrorCode.PROTOCOL_ERROR, "Missing message ID");
        }
        
        return ValidationResult.valid();
    }

    private ValidationResult validateCallErrorMessage(ParsedOcppMessage message) {
        if (!StringUtils.hasText(message.getMessageId())) {
            return ValidationResult.invalid(OcppErrorCode.PROTOCOL_ERROR, "Missing message ID");
        }
        
        if (!StringUtils.hasText(message.getAction())) {
            return ValidationResult.invalid(OcppErrorCode.PROTOCOL_ERROR, "Missing error code");
        }
        
        return ValidationResult.valid();
    }

    /**
     * Validation result.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private OcppErrorCode errorCode;
        private String errorDescription;
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult invalid(OcppErrorCode errorCode, String errorDescription) {
            return new ValidationResult(false, errorCode, errorDescription);
        }
        
        public boolean isInvalid() {
            return !valid;
        }
    }
}
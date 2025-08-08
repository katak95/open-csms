package com.opencsms.ocpp.message.v16.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OCPP 1.6 StatusNotification request message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusNotificationRequest {
    
    @JsonProperty("connectorId")
    private Integer connectorId;
    
    @JsonProperty("errorCode")
    private ChargePointErrorCode errorCode;
    
    @JsonProperty("info")
    private String info;
    
    @JsonProperty("status")
    private ChargePointStatus status;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("vendorId")
    private String vendorId;
    
    @JsonProperty("vendorErrorCode")
    private String vendorErrorCode;
    
    public enum ChargePointErrorCode {
        ConnectorLockFailure,
        EVCommunicationError,
        GroundFailure,
        HighTemperature,
        InternalError,
        LocalListConflict,
        NoError,
        OtherError,
        OverCurrentFailure,
        PowerMeterFailure,
        PowerSwitchFailure,
        ReaderFailure,
        ResetFailure,
        UnderVoltage,
        OverVoltage,
        WeakSignal
    }
    
    public enum ChargePointStatus {
        Available,
        Preparing,
        Charging,
        SuspendedEVSE,
        SuspendedEV,
        Finishing,
        Reserved,
        Unavailable,
        Faulted
    }
}
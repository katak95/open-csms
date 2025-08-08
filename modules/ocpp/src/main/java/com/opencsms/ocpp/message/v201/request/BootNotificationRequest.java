package com.opencsms.ocpp.message.v201.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCPP 2.0.1 BootNotification request message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BootNotificationRequest {
    
    @JsonProperty("chargingStation")
    private ChargingStation chargingStation;
    
    @JsonProperty("reason")
    private BootReason reason;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargingStation {
        
        @JsonProperty("serialNumber")
        private String serialNumber;
        
        @JsonProperty("model")
        private String model;
        
        @JsonProperty("vendorName")
        private String vendorName;
        
        @JsonProperty("firmwareVersion")
        private String firmwareVersion;
        
        @JsonProperty("modem")
        private Modem modem;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Modem {
        
        @JsonProperty("iccid")
        private String iccid;
        
        @JsonProperty("imsi")
        private String imsi;
    }
    
    public enum BootReason {
        ApplicationReset,
        FirmwareUpdate,
        LocalReset,
        PowerUp,
        RemoteReset,
        ScheduledReset,
        Triggered,
        Unknown,
        Watchdog
    }
}
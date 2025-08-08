package com.opencsms.ocpp.message.v201.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OCPP 2.0.1 BootNotification response message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BootNotificationResponse {
    
    @JsonProperty("currentTime")
    private Instant currentTime;
    
    @JsonProperty("interval")
    private Integer interval;
    
    @JsonProperty("status")
    private RegistrationStatus status;
    
    @JsonProperty("statusInfo")
    private StatusInfo statusInfo;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusInfo {
        
        @JsonProperty("reasonCode")
        private String reasonCode;
        
        @JsonProperty("additionalInfo")
        private String additionalInfo;
    }
    
    public enum RegistrationStatus {
        Accepted,
        Pending,
        Rejected
    }
}
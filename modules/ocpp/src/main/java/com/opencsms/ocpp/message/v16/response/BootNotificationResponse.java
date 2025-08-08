package com.opencsms.ocpp.message.v16.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OCPP 1.6 BootNotification response message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BootNotificationResponse {
    
    @JsonProperty("status")
    private RegistrationStatus status;
    
    @JsonProperty("currentTime")
    private Instant currentTime;
    
    @JsonProperty("interval")
    private Integer interval;
    
    public enum RegistrationStatus {
        Accepted,
        Pending,
        Rejected
    }
}
package com.opencsms.ocpp.message.v201.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OCPP 2.0.1 Heartbeat response message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResponse {
    
    @JsonProperty("currentTime")
    private Instant currentTime;
}
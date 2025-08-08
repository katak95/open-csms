package com.opencsms.ocpp.message.v16.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OCPP 1.6 StartTransaction request message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartTransactionRequest {
    
    @JsonProperty("connectorId")
    private Integer connectorId;
    
    @JsonProperty("idTag")
    private String idTag;
    
    @JsonProperty("meterStart")
    private Integer meterStart;
    
    @JsonProperty("reservationId")
    private Integer reservationId;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
}
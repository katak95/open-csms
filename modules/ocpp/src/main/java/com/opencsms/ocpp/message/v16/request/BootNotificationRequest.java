package com.opencsms.ocpp.message.v16.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCPP 1.6 BootNotification request message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BootNotificationRequest {
    
    @JsonProperty("chargePointModel")
    private String chargePointModel;
    
    @JsonProperty("chargePointVendor")
    private String chargePointVendor;
    
    @JsonProperty("chargeBoxSerialNumber")
    private String chargeBoxSerialNumber;
    
    @JsonProperty("chargePointSerialNumber")
    private String chargePointSerialNumber;
    
    @JsonProperty("firmwareVersion")
    private String firmwareVersion;
    
    @JsonProperty("iccid")
    private String iccid;
    
    @JsonProperty("imsi")
    private String imsi;
    
    @JsonProperty("meterSerialNumber")
    private String meterSerialNumber;
    
    @JsonProperty("meterType")
    private String meterType;
}
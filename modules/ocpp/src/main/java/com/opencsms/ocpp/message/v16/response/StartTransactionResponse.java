package com.opencsms.ocpp.message.v16.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCPP 1.6 StartTransaction response message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartTransactionResponse {
    
    @JsonProperty("idTagInfo")
    private IdTagInfo idTagInfo;
    
    @JsonProperty("transactionId")
    private Integer transactionId;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdTagInfo {
        @JsonProperty("status")
        private AuthorizationStatus status;
        
        @JsonProperty("expiryDate")
        private String expiryDate;
        
        @JsonProperty("parentIdTag")
        private String parentIdTag;
    }
    
    public enum AuthorizationStatus {
        Accepted,
        Blocked,
        Expired,
        Invalid,
        ConcurrentTx
    }
}
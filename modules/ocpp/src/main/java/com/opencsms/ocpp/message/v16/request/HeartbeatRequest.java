package com.opencsms.ocpp.message.v16.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCPP 1.6 Heartbeat request message.
 * This message has no payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
    // Empty request - heartbeat has no payload
}
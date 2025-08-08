package com.opencsms.service.station;

import com.opencsms.domain.station.ChargingStation;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Event object for station-related events.
 */
@Data
@Builder
public class StationEvent {
    
    private StationEventService.StationEventType eventType;
    private String stationId;
    private String tenantId;
    private ChargingStation station;
    private String message;
    
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    public static StationEventBuilder builder() {
        return new StationEventBuilder();
    }
}